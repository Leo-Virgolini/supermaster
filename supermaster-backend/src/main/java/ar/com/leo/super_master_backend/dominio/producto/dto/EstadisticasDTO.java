package ar.com.leo.super_master_backend.dominio.producto.dto;

import java.math.BigDecimal;
import java.util.List;

public record EstadisticasDTO(
        // Cards resumen
        int totalProductos,
        int productosActivos,
        int productosSinStock,
        int productosSinCosto,
        int productosSinMargen,
        int productosMargenNegativo,

        // Productos por catalogo (barras)
        List<ProductosPorCatalogo> productosPorCatalogo,

        // Productos por proveedor (top 10)
        List<ProductosPorProveedor> productosPorProveedor,

        // Productos con margen negativo (lista)
        List<ProductoMargenNegativo> productosConMargenNegativo,

        // Cuotas disponibles en el sistema
        List<Integer> cuotasDisponibles
) {

    public record MargenPorCanal(
            Integer canalId,
            String canalNombre,
            BigDecimal margenPromedioSobrePvp,
            BigDecimal margenPromedioSobreIngresoNeto,
            BigDecimal markupPromedio,
            BigDecimal gananciaPromedio,
            int totalPrecios
    ) {}

    public record ProductosPorCatalogo(
            Integer catalogoId,
            String catalogoNombre,
            int cantidad
    ) {}

    public record DistribucionMargenes(
            int negativo,
            int rango0a10,
            int rango10a20,
            int rango20a30,
            int rango30a50,
            int rangoMayor50
    ) {}

    public record ProductosPorProveedor(
            Integer proveedorId,
            String proveedorNombre,
            int cantidad
    ) {}

    public record ProductoMargenNegativo(
            Integer productoId,
            String sku,
            String descripcion,
            String canalNombre,
            Integer cuotas,
            BigDecimal margenSobrePvp,
            BigDecimal ganancia
    ) {}

    public record MargenesPorCuotasDTO(
            List<MargenPorCanal> margenesPorCanal,
            DistribucionMargenes distribucionMargenes
    ) {}

    public record ResumenDTO(
            int totalProductos,
            int productosActivos,
            int productosSinStock,
            int productosSinCosto,
            int productosSinMargen,
            int productosMargenNegativo,
            int productosConMla,
            int productosSinProveedor,
            int productosSinImagen,
            int productosCombos,
            int productosPrio,
            int productosConPrecio,
            List<Integer> cuotasDisponibles
    ) {}

    public record CuotaDisponibleDTO(
            Integer cuotas,
            String descripcion
    ) {}
}
