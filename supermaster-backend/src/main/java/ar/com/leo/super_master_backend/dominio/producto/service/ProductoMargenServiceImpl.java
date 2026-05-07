package ar.com.leo.super_master_backend.dominio.producto.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenPatchDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMargenMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoMargenServiceImpl implements ProductoMargenService {

    private final ProductoMargenRepository repo;
    private final ProductoMargenMapper mapper;
    private final ProductoRepository productoRepository;
    private final ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;

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
        productoRepository.findById(dto.productoId())
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // Buscar configuracion existente
        Optional<ProductoMargen> existente = repo.findByProductoId(dto.productoId());

        ProductoMargen pm;
        BigDecimal margenMinoristaAnterior = null;
        BigDecimal margenMayoristaAnterior = null;
        BigDecimal margenFijoMinoristaAnterior = null;
        BigDecimal margenFijoMayoristaAnterior = null;

        if (existente.isPresent()) {
            pm = existente.get();
            // Guardar valores anteriores para detectar cambios
            margenMinoristaAnterior = pm.getMargenMinorista();
            margenMayoristaAnterior = pm.getMargenMayorista();
            margenFijoMinoristaAnterior = pm.getMargenFijoMinorista();
            margenFijoMayoristaAnterior = pm.getMargenFijoMayorista();

            // Actualizar campos
            mapper.updateEntityFromDTO(dto, pm);
        } else {
            pm = new ProductoMargen();
            pm.setProducto(new Producto(dto.productoId()));
            pm.setMargenMinorista(dto.margenMinorista());
            pm.setMargenMayorista(dto.margenMayorista());
            pm.setMargenFijoMinorista(dto.margenFijoMinorista());
            pm.setMargenFijoMayorista(dto.margenFijoMayorista());
            pm.setObservaciones(dto.observaciones());
        }

        pm = repo.save(pm);

        // Recalcular si cambió algo que afecta el precio
        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());
        boolean cambioMargenFijoMinorista = !Objects.equals(margenFijoMinoristaAnterior, pm.getMargenFijoMinorista());
        boolean cambioMargenFijoMayorista = !Objects.equals(margenFijoMayoristaAnterior, pm.getMargenFijoMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista || cambioMargenFijoMinorista || cambioMargenFijoMayorista) {
            programarRecalculoPostCommit("Recálculo por cambio en margen", dto.productoId());
        }

        return mapper.toDTO(pm);
    }

    @Override
    @Transactional
    public ProductoMargenDTO patch(Integer productoId, ProductoMargenPatchDTO patchDto) {
        if (!presente(patchDto.getMargenMinorista())
                && !presente(patchDto.getMargenMayorista())
                && !presente(patchDto.getMargenFijoMinorista())
                && !presente(patchDto.getMargenFijoMayorista())
                && !presente(patchDto.getObservaciones())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        Optional<ProductoMargen> existente = repo.findByProductoId(productoId);
        ProductoMargen pm = existente.orElseGet(() -> {
            ProductoMargen nuevo = new ProductoMargen();
            nuevo.setProducto(new Producto(productoId));
            return nuevo;
        });

        BigDecimal margenMinoristaAnterior = pm.getMargenMinorista();
        BigDecimal margenMayoristaAnterior = pm.getMargenMayorista();
        BigDecimal margenFijoMinoristaAnterior = pm.getMargenFijoMinorista();
        BigDecimal margenFijoMayoristaAnterior = pm.getMargenFijoMayorista();

        if (presente(patchDto.getMargenMinorista())) {
            pm.setMargenMinorista(leerMargenRequerido(patchDto.getMargenMinorista(), "margenMinorista"));
        } else if (pm.getMargenMinorista() == null) {
            throw new BadRequestException("El campo 'margenMinorista' es requerido para crear el margen");
        }

        if (presente(patchDto.getMargenMayorista())) {
            pm.setMargenMayorista(leerMargenRequerido(patchDto.getMargenMayorista(), "margenMayorista"));
        } else if (pm.getMargenMayorista() == null) {
            throw new BadRequestException("El campo 'margenMayorista' es requerido para crear el margen");
        }

        if (presente(patchDto.getMargenFijoMinorista())) {
            pm.setMargenFijoMinorista(leerDecimalNoNegativoOpcional(patchDto.getMargenFijoMinorista(), "margenFijoMinorista"));
        }
        if (presente(patchDto.getMargenFijoMayorista())) {
            pm.setMargenFijoMayorista(leerDecimalNoNegativoOpcional(patchDto.getMargenFijoMayorista(), "margenFijoMayorista"));
        }
        if (presente(patchDto.getObservaciones())) {
            pm.setObservaciones(leerStringOpcional(patchDto.getObservaciones(), "observaciones", 300));
        }

        pm = repo.save(pm);

        boolean cambioMargenMinorista = !Objects.equals(margenMinoristaAnterior, pm.getMargenMinorista());
        boolean cambioMargenMayorista = !Objects.equals(margenMayoristaAnterior, pm.getMargenMayorista());
        boolean cambioMargenFijoMinorista = !Objects.equals(margenFijoMinoristaAnterior, pm.getMargenFijoMinorista());
        boolean cambioMargenFijoMayorista = !Objects.equals(margenFijoMayoristaAnterior, pm.getMargenFijoMayorista());

        if (cambioMargenMinorista || cambioMargenMayorista || cambioMargenFijoMinorista || cambioMargenFijoMayorista) {
            programarRecalculoPostCommit("Recálculo por cambio en margen", productoId);
        }

        return mapper.toDTO(pm);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId) {
        repo.deleteByProductoId(productoId);
    }


    private BigDecimal leerMargenRequerido(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalRequerido(campo, field);
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre 0 y 100");
        }
        return decimal;
    }

    private BigDecimal leerDecimalRequerido(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        return new BigDecimal(number.toString());
    }

    private BigDecimal leerDecimalNoNegativoOpcional(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' debe ser numérico");
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return decimal;
    }

    private String leerStringOpcional(JsonNullable<String> campo, String field, int maxLength) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' debe ser texto");
        }
        if (text.length() > maxLength) {
            throw new BadRequestException("El campo '" + field + "' no puede exceder " + maxLength + " caracteres");
        }
        return text;
    }


    private boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    private Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    private void programarRecalculoPostCommit(String descripcion, Integer productoId) {
        // Cambio en el margen de UN producto → recalcular SOLO ese producto.
        recalculoPendienteService.marcarProducto(descripcion, productoId);
    }
}






