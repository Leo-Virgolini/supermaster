export interface CanalConceptoCuotaDTO {
	id: number;
	canalId: number;
	canalNombre?: string;
	cuotas: number;
	porcentaje: number;
	descripcion: string;
}

export interface CanalConceptoCuotaUpsertDTO {
	canalId: number;
	cuotas: number;
	porcentaje: number;
	descripcion: string;
}

export type CanalConceptoCuotaPatchDTO = Partial<CanalConceptoCuotaUpsertDTO>;

export interface PageResponse<T> {
	content: T[];
	page: {
		totalElements: number;
		totalPages: number;
	};
}
