export interface MargenPorCanal {
    canalId: number;
    canalNombre: string;
    margenPromedioSobrePvp: number;
    margenPromedioSobreIngresoNeto: number;
    markupPromedio: number;
    gananciaPromedio: number;
    totalPrecios: number;
}

export interface ProductosPorCatalogo {
    catalogoId: number;
    catalogoNombre: string;
    cantidad: number;
}

export interface DistribucionMargenes {
    negativo: number;
    rango0a10: number;
    rango10a20: number;
    rango20a30: number;
    rango30a50: number;
    rangoMayor50: number;
}

export interface ProductosPorProveedor {
    proveedorId: number;
    proveedorNombre: string;
    cantidad: number;
}

export interface ProductoMargenNegativo {
    productoId: number;
    sku: string;
    descripcion: string;
    canalNombre: string;
    cuotas: number | null;
    margenSobrePvp: number;
    ganancia: number;
}

export interface EstadisticasResumenDTO {
    totalProductos: number;
    productosActivos: number;
    productosSinStock: number;
    productosSinCosto: number;
    productosSinMargen: number;
    productosMargenNegativo: number;
    productosConMla: number;
    productosSinProveedor: number;
    productosSinImagen: number;
    productosCombos: number;
    productosPrio: number;
    productosConPrecio: number;
    cuotasDisponibles: number[];
}

export interface CuotaDisponibleDTO {
    cuotas: number;
    descripcion: string | null;
}

export interface EstadisticasDTO extends EstadisticasResumenDTO {
    productosPorCatalogo: ProductosPorCatalogo[];
    productosPorProveedor: ProductosPorProveedor[];
    productosConMargenNegativo: ProductoMargenNegativo[];
}

export interface MargenesPorCuotasDTO {
    margenesPorCanal: MargenPorCanal[];
    distribucionMargenes: DistribucionMargenes;
}
