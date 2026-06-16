export type CampaniaDTO = {
	id: number;
	tnCategoriaId: number;
	nombre: string;
	canalId: number;
	canalNombre: string;
	fechaDesde: string | null;
	fechaHasta: string | null;
	activa: boolean;
	fechaUltimaSync: string | null;
	observaciones: string | null;
	cantidadProductos: number;
};

export type CampaniaProductoDTO = {
	id: number;
	productoId: number;
	sku: string;
	descripcion: string;
	costo: number | null;
	precioManual: number | null;
};

export type SincronizacionResultadoDTO = {
	categoriasImportadas: number;
	productosVinculados: number;
	skusSinMatch: string[];
};
