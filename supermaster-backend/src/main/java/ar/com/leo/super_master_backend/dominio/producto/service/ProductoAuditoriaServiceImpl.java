package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaEntidadBridgeService;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductoAuditoriaServiceImpl implements ProductoAuditoriaService {

    private final AuditoriaEntidadBridgeService auditoriaEntidadBridgeService;
    private final AuditoriaService auditoriaService;

    @Override
    public Map<String, String> capturarSnapshot(Producto producto) {
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("sku", normalizar(producto.getSku()));
        snapshot.put("codExt", normalizar(producto.getCodExt()));
        snapshot.put("tituloDux", normalizar(producto.getTituloDux()));
        snapshot.put("tituloMl", normalizar(producto.getTituloMl()));
        snapshot.put("tituloNube", normalizar(producto.getTituloNube()));
        snapshot.put("esCombo", normalizar(producto.getEsCombo()));
        snapshot.put("uxb", normalizar(producto.getUxb()));
        snapshot.put("moq", normalizar(producto.getMoq()));
        snapshot.put("imagenUrl", normalizar(producto.getImagenUrl()));
        snapshot.put("stock", normalizar(producto.getStock()));
        snapshot.put("activo", normalizar(producto.getActivo()));
        snapshot.put("tagReposicion", producto.getTagReposicion() != null ? producto.getTagReposicion().name() : null);
        snapshot.put("tag", producto.getTag() != null ? producto.getTag().name() : null);
        snapshot.put("costo", decimal(producto.getCosto()));
        snapshot.put("iva", decimal(producto.getIva()));
        snapshot.put("marca", relacion(producto.getMarca() != null ? producto.getMarca().getId() : null, producto.getMarca() != null ? producto.getMarca().getNombre() : null));
        snapshot.put("origen", relacion(producto.getOrigen() != null ? producto.getOrigen().getId() : null, producto.getOrigen() != null ? producto.getOrigen().getNombre() : null));
        snapshot.put("clasifGral", relacion(producto.getClasifGral() != null ? producto.getClasifGral().getId() : null, producto.getClasifGral() != null ? producto.getClasifGral().getNombre() : null));
        snapshot.put("clasifGastro", relacion(producto.getClasifGastro() != null ? producto.getClasifGastro().getId() : null, producto.getClasifGastro() != null ? producto.getClasifGastro().getNombre() : null));
        snapshot.put("tipo", relacion(producto.getTipo() != null ? producto.getTipo().getId() : null, producto.getTipo() != null ? producto.getTipo().getNombre() : null));
        snapshot.put("proveedor", relacion(producto.getProveedor() != null ? producto.getProveedor().getId() : null, producto.getProveedor() != null ? producto.getProveedor().getNombre() : null));
        snapshot.put("material", relacion(producto.getMaterial() != null ? producto.getMaterial().getId() : null, producto.getMaterial() != null ? producto.getMaterial().getNombre() : null));
        snapshot.put("mla", relacion(producto.getMla() != null ? producto.getMla().getId() : null, producto.getMla() != null ? producto.getMla().getMla() : null));
        snapshot.put("capacidad", normalizar(producto.getCapacidad()));
        snapshot.put("largo", normalizar(producto.getLargo()));
        snapshot.put("ancho", normalizar(producto.getAncho()));
        snapshot.put("alto", normalizar(producto.getAlto()));
        snapshot.put("diamboca", normalizar(producto.getDiamboca()));
        snapshot.put("diambase", normalizar(producto.getDiambase()));
        snapshot.put("espesor", normalizar(producto.getEspesor()));
        return snapshot;
    }

    @Override
    @Transactional
    public void registrarCreacion(Producto producto) {
        auditoriaEntidadBridgeService.registrarCreacion(
                AuditoriaEntidad.PRODUCTO,
                producto.getId(),
                producto.getSku(),
                capturarSnapshot(producto)
        );
    }

    @Override
    @Transactional
    public void registrarActualizacion(Integer productoId, Map<String, String> estadoAnterior, Producto productoActual) {
        auditoriaEntidadBridgeService.registrarActualizacion(
                AuditoriaEntidad.PRODUCTO,
                productoId,
                productoActual.getSku(),
                estadoAnterior,
                capturarSnapshot(productoActual)
        );
    }

    @Override
    @Transactional
    public void registrarEliminacion(Integer productoId, Map<String, String> estadoAnterior) {
        auditoriaEntidadBridgeService.registrarEliminacion(
                AuditoriaEntidad.PRODUCTO,
                productoId,
                estadoAnterior.get("sku"),
                estadoAnterior
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarPorProducto(Integer productoId, Pageable pageable) {
        return auditoriaEntidadBridgeService.listarPorEntidad(AuditoriaEntidad.PRODUCTO, productoId, pageable, audit -> audit);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarGlobal(
            String search,
            String accion,
            String campo,
            String origen,
            String usuario,
            Integer productoId,
            Pageable pageable
    ) {
        return auditoriaService.listarGlobal(
                        search,
                        AuditoriaEntidad.PRODUCTO.name(),
                        accion,
                        campo,
                        origen,
                        usuario,
                        productoId,
                        null,
                        null,
                        pageable
                );
    }

    private String relacion(Integer id, String nombre) {
        if (id == null && (nombre == null || nombre.isBlank())) {
            return null;
        }
        if (nombre == null || nombre.isBlank()) {
            return String.valueOf(id);
        }
        if (id == null) {
            return nombre.trim();
        }
        return id + " - " + nombre.trim();
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
