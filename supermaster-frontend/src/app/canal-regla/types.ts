import type { Tag } from "../productos/types";

export type TipoRegla = "INCLUIR" | "EXCLUIR";

export interface CanalReglaDTO {
	id: number;
	canalId: number;
	canalNombre?: string;     // Resuelto en el frontend (no viene del backend)
	tipoRegla: TipoRegla;
	tag?: Tag | null;
	tipoId?: number | null;
	tipoNombre?: string;
	marcaId?: number | null;
	marcaNombre?: string;
	clasifGralId?: number | null;
	clasifGralNombre?: string;
	clasifGastroId?: number | null;
	clasifGastroNombre?: string;
	productoId?: number | null;
	productoLabel?: string;
	tieneEnvio?: boolean | null;
}

export type CanalReglaUpsertDTO = {
	canalId: number;
	tipoRegla: TipoRegla;
	tag?: Tag | null;
	tipoId?: number | null;
	marcaId?: number | null;
	clasifGralId?: number | null;
	clasifGastroId?: number | null;
	productoId?: number | null;
	tieneEnvio?: boolean | null;
};

export type CanalReglaPatchDTO = Partial<CanalReglaUpsertDTO>;

export interface PageResponse<T> {
	content: T[];
	page: {
		totalElements: number;
		totalPages: number;
	};
}
