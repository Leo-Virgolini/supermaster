export interface DescuentoAplicable {
	montoMinimo: number;
	descuentoPorcentaje: number;
	pvpConDescuento: number;
	gananciaConDescuento: number;
	costosVentaConDescuento: number;
	ingresoNetoConDescuento: number;
	margenSobreIngresoNetoConDescuento: number;
	margenSobrePvpConDescuento: number;
	markupConDescuento: number;
}

export interface PrecioCalculado {
	cuotas: number;
	descripcion: string;
	pvp: number;
	pvpInflado?: number;
	precioInfladoCodigo?: string | null;
	precioInfladoTipo?: string | null;
	precioInfladoValor?: number | null;
	ganancia: number;
	margenSobrePvp: number;
	margenSobreIngresoNeto?: number;
	markupPorcentaje?: number;
	costosVenta?: number;
	ingresoNetoVendedor?: number;
	fechaUltimoCalculo?: string | null;
	descuentos?: DescuentoAplicable[] | null;
}

export interface PasoCalculo {
	numeroPaso: number;
	descripcion: string;
	formula: string;
	valor: number;
	detalle: string | null;
	unidad: "moneda" | "porcentaje" | "factor" | null;
}

export interface FormulaCalculo {
	canalNombre: string;
	cuotas: number;
	descripcionCuotas: string;
	formulaGeneral: string;
	pasos: PasoCalculo[];
	resultadoFinal: number;
}

export interface CanalConPrecios {
	canalId: number;
	canalNombre: string;
	precios: PrecioCalculado[];
}

export interface ProductoCanalPrecioDTO {
	id: number;
	sku: string;
	mla: string | null;
	descripcion: string;
	costo: number;
	fechaUltimoCosto?: string | null;
	iva: number;
	tag?: "MAQUINA" | "REPUESTO" | "MENAJE" | null;
	canales: CanalConPrecios[];
}

export interface RecalculoMasivoResultDTO {
    totalPreciosCalculados: number;
    productosIgnoradosSinCosto: number;
    productosIgnoradosSinMargen: number;
    errores: number;
    skusSinCosto: string[];
    skusSinMargen: string[];
    skusConErrores: string[];
}

export interface PageResponse<T> {
	content: T[];
	page: {
		totalElements: number;
		totalPages: number;
		size: number;
		number: number;
	};
}
