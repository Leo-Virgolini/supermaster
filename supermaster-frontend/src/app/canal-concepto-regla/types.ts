import type { Tag } from "../productos/types";

export type TipoRegla = "INCLUIR" | "EXCLUIR";

export interface CanalConceptoReglaDTO {
	id: number;
	canalId: number;
	canalNombre?: string;     // Resuelto en el frontend (no viene del backend)
	conceptoId: number;
	conceptoNombre?: string;  // Resuelto en el frontend (no viene del backend)
	conceptoDescripcion?: string; // Resuelto en el frontend (no viene del backend)
	conceptoPorcentaje?: number;  // Resuelto en el frontend (no viene del backend)
	conceptoAplicaSobre?: string; // Resuelto en el frontend (no viene del backend)
	tipoRegla: TipoRegla;
	tipoId?: number | null;
	tipoNombre?: string;
	clasifGastroId?: number | null;
	clasifGastroNombre?: string;
	clasifGralId?: number | null;
	clasifGralNombre?: string;
	marcaId?: number | null;
	marcaNombre?: string;
	esMaquina?: boolean | null;
	tag?: Tag | null;
	tieneEnvio?: boolean | null;
}

export type CanalConceptoReglaUpsertDTO = {
	canalId: number;
	conceptoId: number;
	tipoRegla: TipoRegla;
	tipoId?: number | null;
	clasifGastroId?: number | null;
	clasifGralId?: number | null;
	marcaId?: number | null;
	esMaquina?: boolean | null;
	tag?: Tag | null;
	tieneEnvio?: boolean | null;
};

export type CanalConceptoReglaPatchDTO = Partial<CanalConceptoReglaUpsertDTO>;

export interface PageResponse<T> {
	content: T[];
	page: {
		totalElements: number;
		totalPages: number;
	};
}
