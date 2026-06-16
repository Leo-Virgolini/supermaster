package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMargenMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;

@Service
@RequiredArgsConstructor
public class ProductoMargenServiceImpl implements ProductoMargenService {

    private final ProductoMargenRepository repo;
    private final ProductoMargenMapper mapper;
    private final ProductoRepository productoRepository;
    private final RecalculoPendienteService recalculoPendienteService;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductoMargenDTO> obtener(Integer productoId) {
        return repo.findByProductoId(productoId)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional
    public ProductoMargenDTO guardar(ProductoMargenDTO dto) {
        // Validar que exista el producto
        Producto producto = productoRepository.findById(dto.productoId())
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Buscar configuracion existente
        Optional<ProductoMargen> existente = repo.findByProductoId(dto.productoId());

        ProductoMargen pm;
        BigDecimal margenMinoristaAnterior = null;
        BigDecimal margenMayoristaAnterior = null;
        Map<String, String> estadoAnterior;
        AuditoriaAccion accion;

        if (existente.isPresent()) {
            pm = existente.get();
            estadoAnterior = capturarSnapshot(pm);
            accion = AuditoriaAccion.UPDATE;
            // Guardar valores anteriores para detectar cambios
            margenMinoristaAnterior = pm.getMargenMinorista();
            margenMayoristaAnterior = pm.getMargenMayorista();

            // Actualizar campos
            mapper.updateEntityFromDTO(dto, pm);
        } else {
            pm = new ProductoMargen();
            pm.setProducto(new Producto(dto.productoId()));
            pm.setMargenMinorista(dto.margenMinorista());
            pm.setMargenMayorista(dto.margenMayorista());
            pm.setObservaciones(dto.observaciones());
            estadoAnterior = Map.of();
            accion = AuditoriaAccion.CREATE;
        }

        pm = repo.save(pm);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_MARGEN,
                pm.getId(),
                producto.getSku(),
                accion,
                estadoAnterior,
                capturarSnapshot(pm)
        );

        // Recalcular si cambió algo que afecta el precio
        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista) {
            recalculoPendienteService.marcarProductoOCalcularInicial(
                    "Cambio en margen del producto", dto.productoId());
        }

        return mapper.toDTO(pm);
    }

    @Override
    @Transactional
    public ProductoMargenDTO patch(Integer productoId, ProductoMargenPatchDTO patchDto) {
        if (!presente(patchDto.getMargenMinorista())
                && !presente(patchDto.getMargenMayorista())
                && !presente(patchDto.getObservaciones())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        Optional<ProductoMargen> existente = repo.findByProductoId(productoId);
        ProductoMargen pm = existente.orElseGet(() -> {
            ProductoMargen nuevo = new ProductoMargen();
            nuevo.setProducto(new Producto(productoId));
            return nuevo;
        });

        Map<String, String> estadoAnterior = existente.isPresent() ? capturarSnapshot(pm) : Map.of();
        AuditoriaAccion accion = existente.isPresent() ? AuditoriaAccion.UPDATE : AuditoriaAccion.CREATE;

        BigDecimal margenMinoristaAnterior = pm.getMargenMinorista();
        BigDecimal margenMayoristaAnterior = pm.getMargenMayorista();

        // Si se está creando el ProductoMargen (no existe aún) y el patch trae solo uno
        // de los dos márgenes, el otro se defaultea a 0 para permitir la edición inline
        // en la tabla. La BD exige ambos NOT NULL; 0% es semánticamente válido.
        if (presente(patchDto.getMargenMinorista())) {
            pm.setMargenMinorista(leerMargenRequerido(patchDto.getMargenMinorista(), "margenMinorista"));
        } else if (pm.getMargenMinorista() == null) {
            pm.setMargenMinorista(BigDecimal.ZERO);
        }

        if (presente(patchDto.getMargenMayorista())) {
            pm.setMargenMayorista(leerMargenRequerido(patchDto.getMargenMayorista(), "margenMayorista"));
        } else if (pm.getMargenMayorista() == null) {
            pm.setMargenMayorista(BigDecimal.ZERO);
        }

        if (presente(patchDto.getObservaciones())) {
            pm.setObservaciones(leerStringOpcional(patchDto.getObservaciones(), "observaciones", 300));
        }

        pm = repo.save(pm);

        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_MARGEN,
                pm.getId(),
                producto.getSku(),
                accion,
                estadoAnterior,
                capturarSnapshot(pm)
        );

        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista) {
            recalculoPendienteService.marcarProductoOCalcularInicial(
                    "Cambio en margen del producto", productoId);
        }

        return mapper.toDTO(pm);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId) {
        Optional<ProductoMargen> existente = repo.findByProductoId(productoId);
        if (existente.isEmpty()) return;
        ProductoMargen pm = existente.get();
        Map<String, String> snapshot = capturarSnapshot(pm);
        String sku = pm.getProducto() != null ? pm.getProducto().getSku() : null;
        Integer pmId = pm.getId();
        repo.deleteByProductoId(productoId);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.PRODUCTO_MARGEN,
                pmId,
                sku,
                AuditoriaAccion.DELETE,
                snapshot,
                Map.of()
        );
    }

    private Map<String, String> capturarSnapshot(ProductoMargen pm) {
        Map<String, String> snap = new LinkedHashMap<>();
        snap.put("productoId", pm.getProducto() != null ? String.valueOf(pm.getProducto().getId()) : "");
        snap.put("margenMinorista", pm.getMargenMinorista() != null ? pm.getMargenMinorista().toPlainString() : "");
        snap.put("margenMayorista", pm.getMargenMayorista() != null ? pm.getMargenMayorista().toPlainString() : "");
        snap.put("observaciones", pm.getObservaciones() != null ? pm.getObservaciones() : "");
        return snap;
    }


    /**
     * Específico: rango [0, 999.999] — el margen es un markup sobre el costo y puede
     * superar el 100% (ej. 100% = duplicar el costo). El tope 999.999 coincide con la
     * precisión de la columna (precision=6, scale=3).
     */
    private BigDecimal leerMargenRequerido(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalRequerido(campo, field);
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(new BigDecimal("999.999")) > 0) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre 0 y 999.999");
        }
        return decimal;
    }

}






