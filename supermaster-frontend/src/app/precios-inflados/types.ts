export type TipoPrecioInflado = "MULTIPLICADOR" | "DESCUENTO_PORC" | "DIVISOR" | "PRECIO_FIJO";

export const TIPO_LABELS: Record<TipoPrecioInflado, string> = {
	MULTIPLICADOR: "Multiplicador",
	DESCUENTO_PORC: "Descuento %",
	DIVISOR: "Divisor",
	PRECIO_FIJO: "Precio Fijo",
};

export interface PrecioInfladoDTO {
	id: number;
	codigo: string;
	tipo: TipoPrecioInflado;
	valor: number;
}

export interface PageResponse<T> {
	content: T[];
	page: {
		totalElements: number;
		totalPages: number;
	};
}
