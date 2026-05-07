export type MlaDTO = {
	id: number;
	mla: string;
	mlau?: string | null;
	precioEnvio?: number | null;
	comisionPorcentaje?: number | null;
	fechaCalculoEnvio?: string | null;
	fechaCalculoComision?: string | null;
	topePromocion?: number | null;
};
