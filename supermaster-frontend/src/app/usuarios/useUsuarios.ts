"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { notificar } from "../utils/notificar";
import {
    changeUsuarioPasswordAPI,
    createUsuarioAPI,
    deleteUsuarioAPI,
    getPermisosAPI,
    getRolesAPI,
    getUsuariosAPI,
    updateRolPermisosAPI,
    updateUsuarioAPI,
} from "./usuariosService";
import type { CambioPasswordDTO, PermisoDTO, RolDTO, UsuarioCreateDTO, UsuarioDTO, UsuarioUpdateDTO } from "./types";

type PageResponse<T> = {
    content: T[];
    page: { totalElements: number; totalPages: number };
};

export function useUsuarios(
    pageIndex: number,
    pageSize: number,
    filters: Record<string, unknown> = {},
    sorting: { id: string; desc: boolean }[] = [],
) {
    const sortParam = sorting.length > 0
        ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}`
        : "id,desc";
    const serializedFilters = JSON.stringify(filters);
    const stableFilters = useMemo(() => JSON.parse(serializedFilters) as Record<string, unknown>, [serializedFilters]);

    const [usuarios, setUsuarios] = useState<UsuarioDTO[]>([]);
    const [roles, setRoles] = useState<RolDTO[]>([]);
    const [permisos, setPermisos] = useState<PermisoDTO[]>([]);
    const [totalRecords, setTotalRecords] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [rolesLoading, setRolesLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const latestRequestIdRef = useRef(0);

    const getUsuarios = useCallback(async () => {
        const requestId = ++latestRequestIdRef.current;
        setIsLoading(true);
        setError(null);
        try {
            const json: PageResponse<UsuarioDTO> = await getUsuariosAPI(pageIndex, pageSize, stableFilters, sortParam);
            if (latestRequestIdRef.current !== requestId) return;
            setUsuarios(json.content || []);
            setTotalRecords(json.page?.totalElements || 0);
        } catch (e: unknown) {
            if (latestRequestIdRef.current !== requestId) return;
            setError(e instanceof Error ? e.message : "Error al cargar usuarios");
            setUsuarios([]);
        } finally {
            if (latestRequestIdRef.current !== requestId) return;
            setIsLoading(false);
        }
    }, [pageIndex, pageSize, stableFilters, sortParam]);

    const getRoles = useCallback(async () => {
        setRolesLoading(true);
        try {
            const [rolesData, permisosData] = await Promise.all([getRolesAPI(), getPermisosAPI()]);
            setRoles(rolesData || []);
            setPermisos(permisosData || []);
        } catch {
            setRoles([]);
            setPermisos([]);
        } finally {
            setRolesLoading(false);
        }
    }, []);

    useEffect(() => {
        getUsuarios();
    }, [getUsuarios]);

    useEffect(() => {
        getRoles();
    }, [getRoles]);

    const createUsuario = async (data: UsuarioCreateDTO) => {
        try {
            const result = await createUsuarioAPI(data);
            await getUsuarios();
            notificar.success(`Usuario #${result.id} creado`);
            return result;
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? e.message : "Error al crear usuario");
            throw e;
        }
    };

    const updateUsuario = async (id: number, data: UsuarioUpdateDTO) => {
        try {
            // El PATCH devuelve el objeto actualizado. Reemplazamos solo esa fila
            // en lugar de refetchar toda la página: evita el skeleton de loading
            // y mantiene scroll / selección intactos.
            const actualizado: UsuarioDTO = await updateUsuarioAPI(id, data);
            setUsuarios((prev) => prev.map((u) => (u.id === id ? { ...u, ...actualizado } : u)));
            notificar.success(`Usuario #${id} actualizado`);
            return actualizado;
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? e.message : "Error al actualizar usuario");
            throw e;
        }
    };

    const changePassword = async (id: number, data: CambioPasswordDTO) => {
        try {
            await changeUsuarioPasswordAPI(id, data);
            notificar.success(`Contraseña actualizada para el usuario #${id}`);
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? e.message : "Error al cambiar contraseña");
            throw e;
        }
    };

    const deleteUsuarios = async (ids: number[]) => {
        try {
            await Promise.all(ids.map((id) => deleteUsuarioAPI(id)));
            await getUsuarios();
            notificar.success(ids.length === 1 ? `Usuario #${ids[0]} eliminado` : `${ids.length} usuarios eliminados`);
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? e.message : "Error al eliminar usuarios");
            throw e;
        }
    };

    const updateRolPermisos = async (rolId: number, permisoIds: number[]) => {
        try {
            await updateRolPermisosAPI(rolId, permisoIds);
            await Promise.all([getRoles(), getUsuarios()]);
            notificar.success("Permisos del rol actualizados");
        } catch (e: unknown) {
            notificar.error(e instanceof Error ? e.message : "Error al actualizar permisos");
            throw e;
        }
    };

    return {
        usuarios,
        roles,
        permisos,
        totalRecords,
        isLoading,
        rolesLoading,
        error,
        createUsuario,
        updateUsuario,
        changePassword,
        deleteUsuarios,
        updateRolPermisos,
    };
}
