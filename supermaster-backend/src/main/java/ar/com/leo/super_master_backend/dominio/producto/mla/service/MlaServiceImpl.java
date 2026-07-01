package ar.com.leo.super_master_backend.dominio.producto.mla.service;

import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.mapper.MlaMapper;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlaServiceImpl implements MlaService {

    private final MlaRepository repo;
    private final MlaMapper mapper;
    private final RecalculoPendienteService recalculoPendienteService;
    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;
    private final AuditoriaService auditoriaService;

    // Inyección @Lazy para evitar cualquier ciclo de dependencias en el arranque
    // (MercadoLibreService arrastra muchas dependencias del dominio de cálculo).
    @Lazy
    @Autowired
    private MercadoLibreService mercadoLibreService;

    // Self-proxy: para que asegurarMla()/obtener() corran en su propia transacción
    // aunque se invoquen desde otro método de este mismo bean (las llamadas this.*
    // no pasan por el proxy de Spring y se saltarían @Transactional).
    @Lazy
    @Autowired
    private MlaServiceImpl self;

    @Override
    @Transactional(readOnly = true)
    public Page<MlaDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByMlaContainingIgnoreCaseOrMlauContainingIgnoreCase(search, search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable).map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MlaDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
    }

    @Override
    @Transactional
    public MlaDTO crear(MlaCreateDTO dto) {
        Mla entity = mapper.toEntity(dto);
        // La columna comision_porcentaje es DECIMAL(5,2): normalizamos por las dudas
        // que llegue con más decimales desde el form de alta.
        if (entity.getComisionPorcentaje() != null) {
            entity.setComisionPorcentaje(entity.getComisionPorcentaje().setScale(2, RoundingMode.HALF_UP));
        }
        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                entity.getId(),
                entity.getMla(),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(entity)
        );
        return mapper.toDTO(entity);
    }

    @Override
    public MlaDTO obtenerOcrearPorSkuDesdeML(String sku) {
        MercadoLibreService.MlaPorSku resultado = mercadoLibreService.buscarMlaPorSku(sku);
        if (resultado == null || resultado.mla() == null || resultado.mla().isBlank()) {
            throw new NotFoundException("No se encontró una publicación tradicional de MercadoLibre para el SKU " + sku);
        }
        final String mlaCode = resultado.mla();

        // Creamos/aseguramos el MLA en una transacción aislada y corta (sin llamadas
        // HTTP dentro). Así, si la consulta a ML falla después, el MLA ya quedó guardado
        // y no arrastra esa transacción a rollback (UnexpectedRollbackException).
        Integer mlaId = self.asegurarMla(mlaCode, resultado.mlau());

        // Envío y comisión con los procesos de ML, FUERA de la tx de creación (cada uno
        // corre en su propia transacción; un fallo de ML no revierte el MLA recién creado).
        // Orden importa: primero la comisión (no requiere producto asociado, usa el ítem
        // de ML); luego el envío, que reutiliza esa comisión sin recalcularla.
        // El envío solo se calcula si el MLA YA tiene un producto asociado (otro producto
        // que comparte el MLA): calcularCostoEnvioGratis hace early-return si no lo tiene.
        // Para un MLA nuevo sin producto, el frontend dispara el envío después de crear y
        // asociar el producto del alta.
        try {
            mercadoLibreService.obtenerCostoVenta(mlaCode);
        } catch (Exception e) {
            log.warn("ML - No se pudo obtener el costo de venta para {}: {}", mlaCode, e.getMessage());
        }
        try {
            mercadoLibreService.calcularCostoEnvioGratis(mlaCode);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular el costo de envío para {}: {}", mlaCode, e.getMessage());
        }

        return self.obtener(mlaId);
    }

    @Override
    public MlaDesdeMlDTO obtenerOcrearPorMlaDesdeML(String mlaCode) {
        MercadoLibreService.MlaPorCodigo resultado = mercadoLibreService.buscarMlaPorCodigo(mlaCode);
        if (resultado == null || resultado.mla() == null || resultado.mla().isBlank()) {
            throw new NotFoundException("No se encontró el ítem " + mlaCode + " en MercadoLibre");
        }
        final String codigo = resultado.mla();

        // Mismo patrón que por-SKU: asegurar el MLA en una tx corta, y luego comisión + envío
        // FUERA de esa tx (cada uno en la suya; un fallo de ML no revierte el MLA recién creado).
        Integer mlaId = self.asegurarMla(codigo, resultado.mlau());
        try {
            mercadoLibreService.obtenerCostoVenta(codigo);
        } catch (Exception e) {
            log.warn("ML - No se pudo obtener el costo de venta para {}: {}", codigo, e.getMessage());
        }
        try {
            mercadoLibreService.calcularCostoEnvioGratis(codigo);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular el costo de envío para {}: {}", codigo, e.getMessage());
        }

        return new MlaDesdeMlDTO(self.obtener(mlaId), resultado.esCatalogo());
    }

    @Override
    @Transactional
    public void asegurarYAsociar(Integer productoId, String mlaCode, String mlau) {
        asegurarYAsociar(productoId, mlaCode, mlau, null, null);
    }

    @Override
    @Transactional
    public void asegurarYAsociar(Integer productoId, String mlaCode, String mlau, String familyId, String familyName) {
        Integer mlaId = self.asegurarMla(mlaCode, mlau, familyId, familyName);
        Mla mla = repo.findById(mlaId)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado tras asegurarlo: " + mlaCode));
        ar.com.leo.super_master_backend.dominio.producto.entity.Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado: " + productoId));
        producto.setMla(mla);
        productoRepository.save(producto);
    }

    @Transactional
    public Integer asegurarMla(String mlaCode, String mlau) {
        return asegurarMla(mlaCode, mlau, null, null);
    }

    /**
     * Crea (o devuelve, si ya existe) el MLA por su código, en su PROPIA transacción
     * y sin llamadas externas dentro. Devuelve el id del MLA. Si vino family (modelo nuevo
     * de ML) y el MLA existente no la tenía, la completa (no pisa valores ya cargados).
     */
    @Transactional
    public Integer asegurarMla(String mlaCode, String mlau, String familyId, String familyName) {
        return repo.findFirstByMla(mlaCode).map(existente -> {
            boolean cambio = false;
            if (familyId != null && existente.getFamilyId() == null) { existente.setFamilyId(familyId); cambio = true; }
            if (familyName != null && existente.getFamilyName() == null) { existente.setFamilyName(familyName); cambio = true; }
            if (cambio) repo.save(existente);
            return existente.getId();
        }).orElseGet(() -> {
            Mla nuevo = new Mla();
            nuevo.setMla(mlaCode);
            nuevo.setMlau(mlau);
            nuevo.setFamilyId(familyId);
            nuevo.setFamilyName(familyName);
            nuevo.setTopePromocion(0);
            Mla guardado = repo.save(nuevo);
            auditoriaService.registrarCambios(
                    AuditoriaEntidad.MLA, guardado.getId(), guardado.getMla(),
                    AuditoriaAccion.CREATE, Map.of(), capturarSnapshot(guardado));
            return guardado.getId();
        });
    }

    @Override
    @Transactional
    public MlaDTO actualizar(Integer id, MlaUpdateDTO dto) {
        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        // Guardar valores anteriores para detectar cambios
        BigDecimal precioEnvioAnterior = entity.getPrecioEnvio();
        BigDecimal comisionPorcentajeAnterior = entity.getComisionPorcentaje();

        mapper.updateEntity(dto, entity);

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                entity.getMla(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        // Solo marcar pendiente si cambió algo que impacta el cálculo de precio.
        // Se marcan SOLO los productos asociados a este MLA (no todos), evitando un
        // recálculo masivo de 5500+ productos cuando puede afectar a 1-50.
        boolean cambioPrecioEnvio = !Objects.equals(precioEnvioAnterior, entity.getPrecioEnvio());
        boolean cambioComision = !Objects.equals(comisionPorcentajeAnterior, entity.getComisionPorcentaje());

        if (cambioPrecioEnvio || cambioComision) {
            marcarProductosDelMla(id, "Cambio en MLA");
        }

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public MlaDTO patch(Integer id, MlaPatchDTO patchDto) {
        if (!presente(patchDto.getMla())
                && !presente(patchDto.getMlau())
                && !presente(patchDto.getPrecioEnvio())
                && !presente(patchDto.getComisionPorcentaje())
                && !presente(patchDto.getTopePromocion())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);

        BigDecimal precioEnvioAnterior = entity.getPrecioEnvio();
        BigDecimal comisionPorcentajeAnterior = entity.getComisionPorcentaje();

        if (presente(patchDto.getMla())) {
            entity.setMla(leerStringRequerido(patchDto.getMla(), "mla", 20));
        }
        if (presente(patchDto.getMlau())) {
            entity.setMlau(leerStringOpcional(patchDto.getMlau(), "mlau", 20));
        }
        if (presente(patchDto.getPrecioEnvio())) {
            entity.setPrecioEnvio(leerDecimalNoNegativoOpcional(patchDto.getPrecioEnvio(), "precioEnvio"));
        }
        if (presente(patchDto.getComisionPorcentaje())) {
            entity.setComisionPorcentaje(leerDecimalNoNegativoOpcional(patchDto.getComisionPorcentaje(), "comisionPorcentaje"));
        }
        if (presente(patchDto.getTopePromocion())) {
            entity.setTopePromocion(leerIntegerNoNegativoRequerido(patchDto.getTopePromocion(), "topePromocion"));
        }

        repo.save(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                entity.getMla(),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(entity)
        );

        boolean cambioPrecioEnvio = !Objects.equals(precioEnvioAnterior, entity.getPrecioEnvio());
        boolean cambioComision = !Objects.equals(comisionPorcentajeAnterior, entity.getComisionPorcentaje());

        if (cambioPrecioEnvio || cambioComision) {
            marcarProductosDelMla(id, "Cambio en MLA");
        }

        return mapper.toDTO(entity);
    }

    private void marcarProductosDelMla(Integer mlaId, String motivo) {
        List<Integer> productoIds = productoRepository.findByMlaId(mlaId).stream()
                .map(p -> p.getId())
                .toList();
        recalculoPendienteService.marcarProductos(motivo, productoIds);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Mla entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("MLA no encontrado"));
        Map<String, String> estadoAnterior = capturarSnapshot(entity);
        String mlaCode = entity.getMla();
        repo.delete(entity);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.MLA,
                id,
                mlaCode,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer mlaId) {
        if (!repo.existsById(mlaId)) {
            throw new NotFoundException("MLA no encontrado");
        }
        return productoRepository.findByMlaId(mlaId)
                .stream()
                .map(productoMapper::toResumenDTO)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    public List<MlaTopePromocionDTO> listarTopesPromocion() {
        return repo.findByTopePromocionGreaterThan(0).stream()
                .map(m -> new MlaTopePromocionDTO(m.getId(), m.getMla(), m.getTopePromocion()))
                .toList();
    }

    @Override
    @Transactional
    public List<MlaTopePromocionDTO> actualizarTopesPromocion(List<MlaTopePromocionDTO> topes) {
        // Resetear todos los topes existentes a 0
        List<Mla> existentes = repo.findByTopePromocionGreaterThan(0);
        for (Mla m : existentes) {
            m.setTopePromocion(0);
            repo.save(m);
        }

        // Aplicar los nuevos topes
        for (MlaTopePromocionDTO dto : topes) {
            if (dto.mla() == null || dto.mla().isBlank() || dto.topePromocion() == null || dto.topePromocion() <= 0) {
                continue;
            }
            repo.findFirstByMla(dto.mla().trim().toUpperCase()).ifPresent(m -> {
                m.setTopePromocion(dto.topePromocion());
                repo.save(m);
            });
        }

        return listarTopesPromocion();
    }

    private Map<String, String> capturarSnapshot(Mla entity) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("mla", normalizar(entity.getMla()));
        snapshot.put("mlau", normalizar(entity.getMlau()));
        snapshot.put("precioEnvio", normalizar(entity.getPrecioEnvio()));
        snapshot.put("comisionPorcentaje", normalizar(entity.getComisionPorcentaje()));
        snapshot.put("topePromocion", normalizar(entity.getTopePromocion()));
        return snapshot;
    }
}

