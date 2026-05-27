export type TagReposicion = 'PRIO' | 'LIQ';
export type Tag = 'MAQUINA' | 'REPUESTO' | 'MENAJE';

export interface ProductoDTO {
	id: number;
	sku: string;
	codExt: string;
	descripcion: string;
	tituloWeb: string;
	esCombo: boolean;
	uxb: number;
	moq: number | null;
	imagenUrl: string | null;
	stock: number | null;
	activo: boolean;
	tagReposicion: TagReposicion | null;
	tag: Tag | null;

	// Relaciones (IDs)
	marcaId: number | null;
	origenId: number | null;
	clasifGralId: number | null;
	clasifGastroId: number | null;
	tipoId: number | null;
	proveedorId: number | null;
	materialId: number | null;
	mlaId: number | null;

	// Dimensiones
	capacidad: string | null;
	largo: string | null;
	ancho: string | null;
	alto: string | null;
	diamboca: string | null;
	diambase: string | null;
	espesor: string | null;

	// Económicos
	costo: number;
	fechaUltimoCosto: string;
	iva: number;

	fechaCreacion: string;
	fechaModificacion: string;

	// Many-to-many (nombres)
	aptos: string[];
	catalogos: string[];
	clientes: string[];

	// Nombres para mostrar en tabla (Campos "Flattened")
	marcaNombre?: string;
	origenNombre?: string;
	clasifGralNombre?: string;
	clasifGastroNombre?: string;
	tipoNombre?: string;
	proveedorNombre?: string;
	materialNombre?: string;
	mlaNombre?: string;

	// Márgenes (vienen de producto_margen, expuestos para edición inline)
	margenMinorista?: number | null;
	margenMayorista?: number | null;
	margenFijoMinorista?: number | null;
	margenFijoMayorista?: number | null;
}

export interface ProductoCreateDTO {
	sku: string;
	codExt: string;
	descripcion: string;
	tituloWeb: string;
	esCombo: boolean;
	uxb: number;
	activo: boolean;
	imagenUrl: string | null;
	tagReposicion: TagReposicion | null;
	tag: Tag | null;
	marcaId: number | null;
	origenId: number | null;
	clasifGralId: number;
	clasifGastroId: number | null;
	tipoId: number;
	proveedorId: number | null;
	materialId: number | null;
	mlaId: number | null;
	capacidad: string | null;
	largo: string | null;
	ancho: string | null;
	alto: string | null;
	diamboca: string | null;
	diambase: string | null;
	espesor: string | null;
	costo: number;
	stock: number | null;
	moq: number | null;
	iva: number;
}

export type ProductoPatchDTO = Partial<ProductoCreateDTO>;
