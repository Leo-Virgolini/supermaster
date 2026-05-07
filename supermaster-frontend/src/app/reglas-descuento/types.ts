export interface ReglaDescuentoDTO {
	id: number;
	canalId: number | null;
	catalogoId: number | null;
	clasifGralId: number | null;
	clasifGastroId: number | null;
	montoMinimo: number;
	descuentoPorcentaje: number;
	prioridad: number;
	activo: boolean;
	descripcion: string | null;
}
