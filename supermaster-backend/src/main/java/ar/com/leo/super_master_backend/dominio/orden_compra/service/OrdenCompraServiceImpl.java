package ar.com.leo.super_master_backend.dominio.orden_compra.service;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.*;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompraLinea;
import ar.com.leo.super_master_backend.dominio.orden_compra.mapper.OrdenCompraMapper;
import ar.com.leo.super_master_backend.dominio.orden_compra.repository.OrdenCompraLineaRepository;
import ar.com.leo.super_master_backend.dominio.orden_compra.repository.OrdenCompraRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra.*;

@Service
@RequiredArgsConstructor
public class OrdenCompraServiceImpl implements OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final OrdenCompraLineaRepository ordenCompraLineaRepository;
    private final OrdenCompraMapper ordenCompraMapper;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<OrdenCompraDTO> listar(Pageable pageable, Integer proveedorId, EstadoOrdenCompra estado, String search) {
        Specification<OrdenCompra> spec = conFiltros(proveedorId, estado, search);
        return ordenCompraRepository.findAll(spec, pageable).map(ordenCompraMapper::toDTO);
    }

    /**
     * Filtro combinado del listado: proveedor (exacto), estado (exacto) y búsqueda de
     * texto. El search matchea (OR) el nombre/apodo del proveedor, las observaciones y
     * —si el término es numérico— el número (id) de la orden. Params null no filtran.
     */
    private Specification<OrdenCompra> conFiltros(Integer proveedorId, EstadoOrdenCompra estado, String search) {
        return (root, query, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            if (proveedorId != null) {
                ands.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
            }
            if (estado != null) {
                ands.add(cb.equal(root.get("estado"), estado));
            }
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase() + "%";
                var proveedor = root.join("proveedor", JoinType.LEFT);
                List<Predicate> ors = new ArrayList<>();
                ors.add(cb.like(cb.lower(proveedor.get("nombre")), like));
                ors.add(cb.like(cb.lower(proveedor.get("apodo")), like));
                ors.add(cb.like(cb.lower(root.get("observaciones")), like));
                try {
                    ors.add(cb.equal(root.get("id"), Integer.parseInt(search.trim())));
                } catch (NumberFormatException ignored) {
                    // término no numérico: solo busca por texto
                }
                ands.add(cb.or(ors.toArray(new Predicate[0])));
            }
            return ands.isEmpty() ? cb.conjunction() : cb.and(ands.toArray(new Predicate[0]));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public OrdenCompraDTO obtener(Integer id) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional
    public OrdenCompraDTO crear(OrdenCompraCreateDTO dto) {
        Proveedor proveedor = proveedorRepository.findById(dto.proveedorId())
                .orElseThrow(() -> new NotFoundException("Proveedor no encontrado"));

        OrdenCompra oc = new OrdenCompra();
        oc.setProveedor(proveedor);
        oc.setEstado(EstadoOrdenCompra.BORRADOR);
        oc.setObservaciones(dto.observaciones());

        for (OrdenCompraLineaCreateDTO lineaDto : dto.lineas()) {
            Producto producto = productoRepository.findById(lineaDto.productoId())
                    .orElseThrow(() -> new NotFoundException("Producto no encontrado: ID " + lineaDto.productoId()));

            OrdenCompraLinea linea = new OrdenCompraLinea();
            linea.setOrdenCompra(oc);
            linea.setProducto(producto);
            linea.setCantidadPedida(lineaDto.cantidadPedida());
            linea.setCantidadRecibida(0);
            linea.setCostoUnitario(lineaDto.costoUnitario());
            oc.getLineas().add(linea);
        }

        ordenCompraRepository.save(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                oc.getId(),
                construirCodigoEntidad(oc),
                AuditoriaAccion.CREATE,
                Map.of(),
                capturarSnapshot(oc)
        );
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional
    public OrdenCompraDTO actualizar(Integer id, OrdenCompraUpdateDTO dto) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(oc);

        if (oc.getEstado() != EstadoOrdenCompra.BORRADOR) {
            throw new BadRequestException("Solo se pueden editar órdenes en estado BORRADOR");
        }

        if (dto.observaciones() != null) {
            oc.setObservaciones(dto.observaciones());
        }

        // Si se envían líneas, reemplazar todas las existentes
        if (dto.lineas() != null) {
            if (dto.lineas().isEmpty()) {
                throw new BadRequestException("La orden debe tener al menos una línea");
            }

            oc.getLineas().clear();
            for (OrdenCompraLineaCreateDTO lineaDto : dto.lineas()) {
                Producto producto = productoRepository.findById(lineaDto.productoId())
                        .orElseThrow(() -> new NotFoundException("Producto no encontrado: ID " + lineaDto.productoId()));

                OrdenCompraLinea linea = new OrdenCompraLinea();
                linea.setOrdenCompra(oc);
                linea.setProducto(producto);
                linea.setCantidadPedida(lineaDto.cantidadPedida());
                linea.setCantidadRecibida(0);
                linea.setCostoUnitario(lineaDto.costoUnitario());
                oc.getLineas().add(linea);
            }
        }

        ordenCompraRepository.save(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                id,
                construirCodigoEntidad(oc),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(oc)
        );
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional
    public OrdenCompraDTO patch(Integer id, OrdenCompraPatchDTO patchDto) {
        if (isPatchVacio(patchDto)) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }

        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(oc);

        if (oc.getEstado() != EstadoOrdenCompra.BORRADOR) {
            throw new BadRequestException("Solo se pueden editar órdenes en estado BORRADOR");
        }

        if (presente(patchDto.getObservaciones())) {
            oc.setObservaciones(leerStringOpcional(patchDto.getObservaciones(), "observaciones", 500));
        }

        if (presente(patchDto.getLineas())) {
            List<OrdenCompraLineaCreateDTO> lineas = leerLineasRequeridas(patchDto.getLineas(), "lineas");
            if (lineas.isEmpty()) {
                throw new BadRequestException("La orden debe tener al menos una línea");
            }

            oc.getLineas().clear();
            for (OrdenCompraLineaCreateDTO lineaDto : lineas) {
                Producto producto = productoRepository.findById(lineaDto.productoId())
                        .orElseThrow(() -> new NotFoundException("Producto no encontrado: ID " + lineaDto.productoId()));

                OrdenCompraLinea linea = new OrdenCompraLinea();
                linea.setOrdenCompra(oc);
                linea.setProducto(producto);
                linea.setCantidadPedida(lineaDto.cantidadPedida());
                linea.setCantidadRecibida(0);
                linea.setCostoUnitario(lineaDto.costoUnitario());
                oc.getLineas().add(linea);
            }
        }

        ordenCompraRepository.save(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                id,
                construirCodigoEntidad(oc),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(oc)
        );
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(oc);
        String codigo = construirCodigoEntidad(oc);

        if (oc.getEstado() != EstadoOrdenCompra.BORRADOR) {
            throw new BadRequestException("Solo se pueden eliminar órdenes en estado BORRADOR");
        }

        ordenCompraRepository.delete(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                id,
                codigo,
                AuditoriaAccion.DELETE,
                estadoAnterior,
                Map.of()
        );
    }

    @Override
    @Transactional
    public OrdenCompraDTO enviar(Integer id) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(oc);

        if (oc.getEstado() != EstadoOrdenCompra.BORRADOR) {
            throw new BadRequestException("Solo se pueden enviar órdenes en estado BORRADOR");
        }

        oc.setEstado(EstadoOrdenCompra.ENVIADA);
        ordenCompraRepository.save(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                id,
                construirCodigoEntidad(oc),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(oc)
        );
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional
    public OrdenCompraDTO registrarRecepcion(Integer id, RecepcionDTO dto) {
        OrdenCompra oc = ordenCompraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshot(oc);

        if (oc.getEstado() != EstadoOrdenCompra.ENVIADA && oc.getEstado() != EstadoOrdenCompra.RECIBIDA_PARCIAL) {
            throw new BadRequestException("Solo se puede registrar recepción en órdenes ENVIADA o RECIBIDA_PARCIAL");
        }

        // Crear mapa de líneas por ID para acceso rápido
        Map<Integer, OrdenCompraLinea> lineasMap = new HashMap<>();
        for (OrdenCompraLinea linea : oc.getLineas()) {
            lineasMap.put(linea.getId(), linea);
        }

        for (RecepcionDTO.LineaRecepcionDTO lineaRecepcion : dto.lineas()) {
            OrdenCompraLinea linea = lineasMap.get(lineaRecepcion.lineaId());
            if (linea == null) {
                throw new NotFoundException("Línea no encontrada: ID " + lineaRecepcion.lineaId());
            }

            int nuevaCantidad = lineaRecepcion.cantidadRecibida();
            if (nuevaCantidad > linea.getCantidadPedida()) {
                throw new BadRequestException(
                        String.format("La cantidad recibida (%d) no puede exceder la pedida (%d) para la línea %d",
                                nuevaCantidad, linea.getCantidadPedida(), linea.getId()));
            }

            linea.setCantidadRecibida(nuevaCantidad);
        }

        // Determinar nuevo estado
        boolean todasCompletas = oc.getLineas().stream()
                .allMatch(l -> l.getCantidadRecibida() >= l.getCantidadPedida());
        boolean algunaRecibida = oc.getLineas().stream()
                .anyMatch(l -> l.getCantidadRecibida() > 0);

        if (todasCompletas) {
            oc.setEstado(EstadoOrdenCompra.COMPLETA);
        } else if (algunaRecibida) {
            oc.setEstado(EstadoOrdenCompra.RECIBIDA_PARCIAL);
        }

        ordenCompraRepository.save(oc);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.ORDEN_COMPRA,
                id,
                construirCodigoEntidad(oc),
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshot(oc)
        );
        return ordenCompraMapper.toDTO(oc);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, Integer> obtenerPendientesPorProducto() {
        List<Object[]> resultados = ordenCompraLineaRepository.findPendientesPorProducto(
                List.of(ENVIADA, RECIBIDA_PARCIAL));
        Map<Integer, Integer> pendientes = new HashMap<>();
        for (Object[] row : resultados) {
            Integer productoId = (Integer) row[0];
            Long cantidad = (Long) row[1];
            pendientes.put(productoId, cantidad.intValue());
        }
        return pendientes;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Integer, UltimaCompra> obtenerUltimaCompraPorProducto() {
        List<Object[]> resultados = ordenCompraLineaRepository.findUltimasCompras(
                List.of(BORRADOR, CANCELADA));
        // Ordenados por fecha DESC, tomo solo la primera ocurrencia por producto
        Map<Integer, UltimaCompra> ultimaCompra = new HashMap<>();
        for (Object[] row : resultados) {
            Integer productoId = (Integer) row[0];
            if (!ultimaCompra.containsKey(productoId)) {
                LocalDateTime fecha = (LocalDateTime) row[1];
                Integer cantidad = (Integer) row[2];
                ultimaCompra.put(productoId, new UltimaCompra(fecha, cantidad));
            }
        }
        return ultimaCompra;
    }


    private boolean isPatchVacio(OrdenCompraPatchDTO patchDto) {
        return !presente(patchDto.getObservaciones())
                && !presente(patchDto.getLineas());
    }

    /** Específico: lista anidada con validación por elemento (productoId + cantidadPedida + costoUnitario). */
    private List<OrdenCompraLineaCreateDTO> leerLineasRequeridas(JsonNullable<List<OrdenCompraLineaCreateDTO>> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof List<?> list)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser una lista");
        }
        @SuppressWarnings("unchecked")
        List<OrdenCompraLineaCreateDTO> lineas = (List<OrdenCompraLineaCreateDTO>) list;
        for (OrdenCompraLineaCreateDTO linea : lineas) {
            if (linea == null) {
                throw new BadRequestException("Cada línea debe ser un objeto válido");
            }
            if (linea.productoId() == null || linea.productoId() <= 0) {
                throw new BadRequestException("El campo 'productoId' debe ser positivo");
            }
            if (linea.cantidadPedida() == null || linea.cantidadPedida() <= 0) {
                throw new BadRequestException("El campo 'cantidadPedida' debe ser mayor a 0");
            }
            if (linea.costoUnitario() != null && linea.costoUnitario().compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new BadRequestException("El campo 'costoUnitario' debe ser mayor o igual a 0");
            }
        }
        return lineas;
    }

    private Map<String, String> capturarSnapshot(OrdenCompra oc) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("proveedor", describirProveedor(oc.getProveedor()));
        snapshot.put("estado", normalizar(oc.getEstado()));
        snapshot.put("observaciones", normalizar(oc.getObservaciones()));
        snapshot.put("lineas", resumirLineas(oc.getLineas()));
        return snapshot;
    }

    private String construirCodigoEntidad(OrdenCompra oc) {
        return "OC #" + (oc.getId() != null ? oc.getId() : "nueva") + " / "
                + (oc.getProveedor() != null ? oc.getProveedor().getApodo() : "Sin proveedor");
    }

    private String describirProveedor(Proveedor proveedor) {
        return proveedor == null ? null : proveedor.getId() + " - " + proveedor.getApodo();
    }

    private String resumirLineas(List<OrdenCompraLinea> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            return null;
        }
        return lineas.stream()
                .map(linea -> {
                    Producto producto = linea.getProducto();
                    String sku = producto != null ? producto.getSku() : "SIN SKU";
                    return sku
                            + " x" + linea.getCantidadPedida()
                            + " rec:" + linea.getCantidadRecibida()
                            + " $" + normalizar(linea.getCostoUnitario());
                })
                .collect(Collectors.joining(" | "));
    }

}

