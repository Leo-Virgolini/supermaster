export type ConceptoGastoDTO = {
	id: number;
	concepto: string;
	porcentaje: number;
	aplicaSobre: string; // 'PVP', 'COSTO', etc.
	etapa: string; // 'COSTO' | 'MARGEN' | 'IMPUESTOS' | 'PRECIO' | 'POST_PRECIO' — derivado por backend desde aplicaSobre
	naturaleza: string; // 'COSTO_VENTA' | 'INFLACION' | etc. — override del concepto o default del aplicaSobre
};
