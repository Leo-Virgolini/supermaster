export interface AuditoriaCambioDTO {
	id: number;
	entidad: string;
	entidadId: number | null;
	entidadCodigo?: string | null;
	accion: string;
	campo: string;
	valorAnterior: string | null;
	valorNuevo: string | null;
	usuarioId: number | null;
	usuarioUsername: string | null;
	usuarioNombreCompleto: string | null;
	origen: string;
	fechaHora: string;
}
