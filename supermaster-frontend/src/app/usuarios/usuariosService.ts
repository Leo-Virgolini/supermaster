import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import type { CambioPasswordDTO, PermisoDTO, RolDTO, UsuarioCreateDTO, UsuarioDTO, UsuarioUpdateDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/usuarios`;

export const getUsuariosAPI = async (
    page: number,
    size: number,
    filters: Record<string, unknown> = {},
    sort = "id,desc",
) => {
    const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sort,
    });

    Object.entries(filters).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            params.append(key, String(value));
        }
    });

    const response = await fetchAPI(`${API_URL}?${params.toString()}`);
    if (!response.ok) throw new Error("Error al obtener usuarios");
    return await response.json();
};

export const getRolesAPI = async (): Promise<RolDTO[]> => {
    const response = await fetchAPI(`${API_URL}/roles`);
    if (!response.ok) throw new Error("Error al obtener roles");
    return await response.json();
};

export const createUsuarioAPI = async (data: UsuarioCreateDTO): Promise<UsuarioDTO> => {
    const response = await fetchAPI(API_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
        body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error("Error al crear usuario");
    return await response.json();
};

export const updateUsuarioAPI = async (id: number, data: UsuarioUpdateDTO): Promise<UsuarioDTO> => {
    const response = await fetchAPI(`${API_URL}/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
        body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error("Error al actualizar usuario");
    return await response.json();
};

export const changeUsuarioPasswordAPI = async (id: number, data: CambioPasswordDTO): Promise<void> => {
    const response = await fetchAPI(`${API_URL}/${id}/password`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
        body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error("Error al cambiar contraseña");
};

export const deleteUsuarioAPI = async (id: number): Promise<void> => {
    const response = await fetchAPI(`${API_URL}/${id}`, {
        method: "DELETE",
        headers: { "X-Audit-Origin": "TABLE" },
    });
    if (!response.ok) throw new Error("Error al eliminar usuario");
};

const ROLES_URL = `${API_BASE_URL}/api/roles`;

export const getPermisosAPI = async (): Promise<PermisoDTO[]> => {
    const response = await fetchAPI(`${ROLES_URL}/permisos`);
    if (!response.ok) throw new Error("Error al obtener permisos");
    return await response.json();
};

export const updateRolPermisosAPI = async (rolId: number, permisoIds: number[]): Promise<RolDTO> => {
    const response = await fetchAPI(`${ROLES_URL}/${rolId}/permisos`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
        body: JSON.stringify({ permisoIds }),
    });
    if (!response.ok) throw new Error("Error al actualizar permisos del rol");
    return await response.json();
};
