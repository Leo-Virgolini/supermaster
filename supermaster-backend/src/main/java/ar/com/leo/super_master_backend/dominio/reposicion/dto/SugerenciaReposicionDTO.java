package ar.com.leo.super_master_backend.dominio.reposicion.dto;

import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;

import java.time.LocalDateTime;

public record SugerenciaReposicionDTO(
        Integer productoId,
        String sku,
        String codExt,
        String descripcion,
        String proveedorNombre,
        Integer uxb,
        Integer moq,
        TagReposicion tagReposicion,
        int stockActual,
        int pendienteClientes,
        int pendienteProveedores,
        int saldoDisponible,
        int ventasMes1,
        int ventasMes2,
        int ventasMes3,
        double promedioVentas,
        double promedioDiario,
        int puntoReorden,
        boolean urgente,
        int sugerencia,
        int pedido,
        LocalDateTime ultimaCompraFecha,
        int ultimaCompraCantidad
) {
}
