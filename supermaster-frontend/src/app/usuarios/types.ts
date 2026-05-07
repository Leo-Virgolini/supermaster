export type RolDTO = {
    id: number;
    nombre: string;
    descripcion: string | null;
    permisos: string[];
};

export type UsuarioDTO = {
    id: number;
    username: string;
    nombreCompleto: string;
    activo: boolean;
    rol: RolDTO;
    permisos: string[];
    fechaModificacion: string | null;
    ultimoLogin: string | null;
};

export type UsuarioCreateDTO = {
    username: string;
    password: string;
    nombreCompleto: string;
    rolId: number;
};

export type UsuarioUpdateDTO = {
    nombreCompleto?: string;
    activo?: boolean;
    rolId?: number;
};

export type CambioPasswordDTO = {
    nuevaPassword: string;
};

export type PermisoDTO = {
    id: number;
    nombre: string;
    descripcion: string | null;
};
