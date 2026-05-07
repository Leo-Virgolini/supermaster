export type ProveedorDTO = {
	id: number;
	proveedor: string;
	apodo?: string;
	plazoPago?: string;
	entrega?: boolean;
	financiacionPorcentaje?: number | null;
	leadTimeDias?: number | null;
};
