export type TagReposicion = 'PRIO' | 'LIQ';
export type Tag = 'MAQUINA' | 'REPUESTO' | 'MENAJE' | 'INSUMO';

export interface ProductoDTO {
	id: number;
	sku: string;
	codExt: string;
	tituloDux: string;
	tituloMl: string | null;
	tituloNube: string | null;
	mlCategoryId: string | null;
	mlCategoryNombre: string | null;
	esCombo: boolean;
	uxb: number;
	moq: number | null;
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
	sectorDepositoId: number | null;
	mlaId: number | null;

	// Dimensiones
	capacidad: string | null;
	largo: string | null;
	ancho: string | null;
	alto: string | null;
	diamboca: string | null;
	diambase: string | null;
	espesor: string | null;

	// Dimensiones paquete ML
	mlPaqAlto?: number | null;
	mlPaqAncho?: number | null;
	mlPaqLargo?: number | null;
	mlPaqPeso?: number | null;

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

	// Paths jerárquicos completos "ABUELO > PADRE > HIJO" (del backend) para
	// mostrar la herencia en la tabla sin un fetch por celda.
	marcaNombreCompleto?: string | null;
	tipoNombreCompleto?: string | null;
	clasifGralNombreCompleto?: string | null;
	clasifGastroNombreCompleto?: string | null;

	// Márgenes (vienen de producto_margen, expuestos para edición inline)
	margenMinorista?: number | null;
	margenMayorista?: number | null;
}

export interface ProductoCreateDTO {
	sku: string;
	codExt: string;
	tituloDux: string;
	tituloMl: string | null;
	tituloNube: string | null;
	mlCategoryId: string | null;
	mlCategoryNombre: string | null;
	esCombo: boolean;
	uxb: number;
	activo: boolean;
	tagReposicion: TagReposicion | null;
	tag: Tag | null;
	marcaId: number | null;
	origenId: number | null;
	clasifGralId: number;
	clasifGastroId: number | null;
	tipoId: number;
	proveedorId: number | null;
	materialId: number | null;
	sectorDepositoId: number | null;
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
	mlPaqAlto?: number | null;
	mlPaqAncho?: number | null;
	mlPaqLargo?: number | null;
	mlPaqPeso?: number | null;
}

export type ProductoPatchDTO = Partial<ProductoCreateDTO>;
