export type SectorDepositoDTO = {
	id: number;
	codigo: string;
	idDux: number | null;
};

export type SectorDepositoCreateDTO = {
	codigo: string;
	idDux?: number | null;
};

export type SectorDepositoPatchDTO = {
	codigo?: string;
	idDux?: number | null;
};
