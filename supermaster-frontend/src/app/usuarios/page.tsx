"use client";

import { useMemo, useState } from "react";
import { type SortingState } from "@tanstack/react-table";
import {
    CheckIcon,
    KeyIcon,
    ShieldCheckIcon,
    TrashIcon,
    UserGroupIcon,
    UserPlusIcon,
    XMarkIcon,
} from "@heroicons/react/24/outline";
import { notificar } from "../utils/notificar";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { useAuth } from "../context/AuthContext";
import { confirmDialog } from "../utils/confirmDialog";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import { getColumns } from "./columns";
import { useUsuarios } from "./useUsuarios";
import { getUsuariosAPI } from "./usuariosService";
import { getRoleBadgeClasses } from "../utils/roleBadge";
import type { PermisoDTO, RolDTO, UsuarioCreateDTO, UsuarioDTO } from "./types";

type UsuarioFormState = {
    username: string;
    password: string;
    nombreCompleto: string;
    rolId: number | "";
    activo: boolean;
};

const emptyForm: UsuarioFormState = {
    username: "",
    password: "",
    nombreCompleto: "",
    rolId: "",
    activo: true,
};

function PermissionChip({ permiso }: { permiso: string }) {
    return (
        <span className="inline-flex rounded-full border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] font-medium text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {permiso}
        </span>
    );
}

function RoleCard({ rol, allPermisos, canEdit, onSave }: {
    rol: RolDTO;
    allPermisos: PermisoDTO[];
    canEdit: boolean;
    onSave: (rolId: number, permisoIds: number[]) => Promise<void>;
}) {
    const [isEditing, setIsEditing] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set());
    const [saving, setSaving] = useState(false);

    const grouped = useMemo(() => {
        const map: Record<string, PermisoDTO[]> = {};
        for (const p of allPermisos) {
            const parts = p.nombre.split("_");
            const grupo = parts.length > 1 ? parts.slice(0, -1).join("_") : p.nombre;
            (map[grupo] ??= []).push(p);
        }
        return Object.entries(map).sort(([a], [b]) => a.localeCompare(b));
    }, [allPermisos]);

    const startEditing = () => {
        const ids = new Set(allPermisos.filter((p) => rol.permisos.includes(p.nombre)).map((p) => p.id));
        setSelected(ids);
        setIsEditing(true);
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            await onSave(rol.id, Array.from(selected));
            setIsEditing(false);
        } finally {
            setSaving(false);
        }
    };

    const togglePermiso = (id: number) => {
        setSelected((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    };

    const toggleGrupo = (permisos: PermisoDTO[]) => {
        setSelected((prev) => {
            const next = new Set(prev);
            const allSelected = permisos.every((p) => next.has(p.id));
            for (const p of permisos) {
                if (allSelected) next.delete(p.id); else next.add(p.id);
            }
            return next;
        });
    };

    return (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-800/80">
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100">
                        <span className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-bold ${getRoleBadgeClasses(rol.nombre)}`}>
                            {rol.nombre}
                        </span>
                    </h3>
                    <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{rol.descripcion || "Sin descripción"}</p>
                </div>
                <div className="flex items-center gap-2">
                    <span className="rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-semibold text-blue-700 dark:bg-blue-500/10 dark:text-blue-300">
                        {isEditing ? selected.size : rol.permisos.length} permisos
                    </span>
                    {canEdit && !isEditing && (
                        <button onClick={startEditing} className="inline-flex items-center gap-1 rounded-lg border border-blue-300 bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-100 dark:border-blue-700 dark:bg-blue-500/10 dark:text-blue-300 dark:hover:bg-blue-500/20 transition">
                            <ShieldCheckIcon className="w-3.5 h-3.5" />
                            Editar permisos
                        </button>
                    )}
                </div>
            </div>

            {isEditing ? (
                <div className="mt-4 space-y-3">
                    {grouped.map(([grupo, permisos]) => {
                        const allChecked = permisos.every((p) => selected.has(p.id));
                        return (
                            <div key={grupo} className="rounded-xl border border-slate-200 bg-slate-50/70 p-3 dark:border-slate-700 dark:bg-slate-900/40">
                                <label className="flex items-center gap-2 mb-2 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={allChecked}
                                        onChange={() => toggleGrupo(permisos)}
                                        className="w-3.5 h-3.5 accent-blue-600"
                                    />
                                    <span className="text-xs font-semibold text-slate-700 dark:text-slate-200 uppercase tracking-wide">{grupo.replace(/_/g, " ")}</span>
                                </label>
                                <div className="flex flex-wrap gap-2 ml-5">
                                    {permisos.map((p) => (
                                        <label key={p.id} className="inline-flex items-center gap-1.5 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={selected.has(p.id)}
                                                onChange={() => togglePermiso(p.id)}
                                                className="w-3 h-3 accent-blue-600"
                                            />
                                            <span className={`text-[11px] font-medium ${selected.has(p.id) ? "text-slate-800 dark:text-slate-100" : "text-slate-400 dark:text-slate-500"}`}>
                                                {p.nombre.split("_").pop()}
                                            </span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                        );
                    })}
                    <div className="flex gap-2 pt-2 border-t border-slate-200 dark:border-slate-700">
                        <button
                            onClick={handleSave}
                            disabled={saving}
                            className="inline-flex items-center gap-1 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-50 transition"
                        >
                            <CheckIcon className="w-3.5 h-3.5" />
                            {saving ? "Guardando..." : "Guardar"}
                        </button>
                        <button
                            onClick={() => setIsEditing(false)}
                            disabled={saving}
                            className="inline-flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700 disabled:opacity-50 transition"
                        >
                            <XMarkIcon className="w-3.5 h-3.5" />
                            Cancelar
                        </button>
                    </div>
                </div>
            ) : (
                <div className="mt-3 flex flex-wrap gap-2">
                    {rol.permisos.length > 0 ? rol.permisos.map((permiso) => <PermissionChip key={`${rol.id}-${permiso}`} permiso={permiso} />) : (
                        <span className="text-xs text-slate-400 dark:text-slate-500">Sin permisos definidos</span>
                    )}
                </div>
            )}
        </div>
    );
}

export default function UsuariosPage() {
    const { usuario } = useAuth();
    const isAdmin = (usuario.rol || "").trim().toUpperCase() === "ADMIN";
    const canView = isAdmin;
    const canEdit = isAdmin;

    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("usuarios"));
    const [filters, setFilters] = useState<Record<string, string>>({ search: "" });
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isPasswordOpen, setIsPasswordOpen] = useState(false);
    const [editingUsuario, setEditingUsuario] = useState<UsuarioDTO | null>(null);
    const [passwordUsuario, setPasswordUsuario] = useState<UsuarioDTO | null>(null);
    const [formTouched, setFormTouched] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [passwordTouched, setPasswordTouched] = useState(false);
    const [form, setForm] = useState<UsuarioFormState>(emptyForm);
    const [nuevaPassword, setNuevaPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const {
        usuarios,
        roles,
        permisos: allPermisos,
        totalRecords,
        isLoading,
        rolesLoading,
        error,
        createUsuario,
        updateUsuario,
        changePassword,
        deleteUsuarios,
        updateRolPermisos,
    } = useUsuarios(pageIndex, pageSize, filters, sorting);

    const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
    const selectedIds = Object.keys(rowSelection).map(Number);
    const hasSelection = selectedIds.length > 0;
    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? `${sorting[0].id},${sorting[0].desc ? "desc" : "asc"}` : "id,desc";
        const res = await getUsuariosAPI(0, 99999, filters, sortParam);
        return res.content;
    };

    const resetForm = () => {
        setForm(emptyForm);
        setEditingUsuario(null);
        setFormTouched(false);
    };

    const openCreate = () => {
        resetForm();
        setIsFormOpen(true);
    };

    const openEdit = (usuario: UsuarioDTO) => {
        setEditingUsuario(usuario);
        setForm({
            username: usuario.username,
            password: "",
            nombreCompleto: usuario.nombreCompleto,
            rolId: usuario.rol?.id ?? "",
            activo: !!usuario.activo,
        });
        setFormTouched(false);
        setIsFormOpen(true);
    };

    const openPassword = (usuario: UsuarioDTO) => {
        setPasswordUsuario(usuario);
        setNuevaPassword("");
        setConfirmPassword("");
        setPasswordTouched(false);
        setIsPasswordOpen(true);
    };

    const closeForm = () => {
        setIsFormOpen(false);
        resetForm();
    };

    const closePassword = () => {
        setIsPasswordOpen(false);
        setPasswordUsuario(null);
        setNuevaPassword("");
        setConfirmPassword("");
        setPasswordTouched(false);
    };

    const handleSave = async () => {
        setFormTouched(true);
        const baseValid = form.nombreCompleto.trim() && form.rolId !== "";
        const createValid = form.username.trim() && form.password.trim().length >= 6;
        if (!baseValid || (!editingUsuario && !createValid)) return;

        setIsSaving(true);
        try {
            if (editingUsuario) {
                await updateUsuario(editingUsuario.id, {
                    nombreCompleto: form.nombreCompleto.trim(),
                    rolId: Number(form.rolId),
                    activo: form.activo,
                });
                notificar.success("Usuario actualizado");
            } else {
                const payload: UsuarioCreateDTO = {
                    username: form.username.trim(),
                    password: form.password,
                    nombreCompleto: form.nombreCompleto.trim(),
                    rolId: Number(form.rolId),
                };
                await createUsuario(payload);
                notificar.success("Usuario creado");
            }
            closeForm();
        } finally {
            setIsSaving(false);
        }
    };

    const handlePasswordSave = async () => {
        setPasswordTouched(true);
        if (!passwordUsuario) return;
        if (nuevaPassword.trim().length < 6 || nuevaPassword !== confirmPassword) return;

        setIsSaving(true);
        try {
            await changePassword(passwordUsuario.id, { nuevaPassword });
            closePassword();
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!hasSelection) return;
        const usernames = selectedIds.map((id) => usuarios.find((u) => u.id === id)?.username).filter(Boolean);
        const detalle = usernames.length <= 3 ? usernames.join(", ") : `${usernames.slice(0, 3).join(", ")} y ${usernames.length - 3} más`;
        if (!(await confirmDialog({ title: "Eliminar usuarios", message: `¿Eliminar ${selectedIds.length === 1 ? `"${detalle}"` : `${selectedIds.length} usuarios (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" }))) return;
        await deleteUsuarios(selectedIds);
        setRowSelection({});
    };

    const handleGlobalSearch = (valor: string) => {
        setFilters((prev) => ({ ...prev, search: valor }));
        setPageIndex(0);
    };

    // `hasActiveFilters`: hay filtros por columna activos (no contamos search,
    // porque eso ya lo detecta el Table vía globalFilter).
    const hasActiveFilters = Object.entries(filters).some(([key, value]) => {
        if (key === "search") return false;
        if (value === undefined || value === null || value === "") return false;
        if (Array.isArray(value) && value.length === 0) return false;
        return true;
    });

    const clearAllFilters = () => {
        setFilters({});
        setPageIndex(0);
    };

    const columns = useMemo(() => getColumns(openEdit, openPassword, canEdit), [canEdit]);

    if (!canView) {
        return (
            <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
                <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-amber-800 dark:border-amber-800 dark:bg-amber-500/10 dark:text-amber-200">
                    Solo los administradores pueden acceder a Usuarios y Accesos.
                </div>
            </main>
        );
    }

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden dark:bg-slate-900">
            <div className="flex justify-between items-center mb-3">
                <div>
                    <h1 className="text-3xl font-bold text-gray-800 dark:text-slate-100 flex items-center gap-2">
                        <UserGroupIcon className="w-8 h-8 text-gray-600 dark:text-slate-400" />
                        Usuarios y Accesos
                    </h1>
                    <p className="mt-1 text-sm text-gray-500 dark:text-slate-400">
                        Administrá usuarios del sistema, roles disponibles y permisos heredados.
                    </p>
                </div>
                <div className="flex gap-2 items-center">
                    {hasSelection && canEdit ? <Button variant="danger" onClick={handleDelete}><TrashIcon className="w-4 h-4" /> Borrar ({selectedIds.length})</Button> : null}
                    {canEdit ? <Button variant="dark" onClick={openCreate}><UserPlusIcon className="w-4 h-4" /> Crear Usuario</Button> : null}
                </div>
            </div>

            {error ? <ErrorBanner message={error} /> : (
                <div className="flex-1 min-h-0 overflow-auto flex flex-col gap-4">
                    <div className="min-h-0 flex flex-col">
                        <Table
                            searchSlot={<SearchInput placeholder="Buscar por usuario o nombre..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
                            tableId="usuarios"
                            isLoading={isLoading}
                            data={usuarios}
                            columns={columns}
                            globalFilter={filters.search}
                            setGlobalFilter={handleGlobalSearch}
                            pageCount={pageCount}
                            pageIndex={pageIndex}
                            pageSize={pageSize}
                            onPageChange={setPageIndex}
                            onPageSizeChange={setPageSize}
                            totalRecords={totalRecords}
                            sorting={sorting}
                            setSorting={setSorting}
                            rowSelection={rowSelection}
                            setRowSelection={setRowSelection}
                            onColumnFilterChange={() => {}}
                            hasFiltersActive={hasActiveFilters}
                            onClearAllFilters={clearAllFilters}
                            getActiveFilter={() => undefined}
                            onExportAll={handleExportAll}
                            exportFilename="usuarios"
                        />
                    </div>

                    <section className="space-y-3">
                        <div className="flex items-center gap-2">
                            <ShieldCheckIcon className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                            <h2 className="text-lg font-semibold text-slate-800 dark:text-slate-100">Roles y permisos disponibles</h2>
                        </div>
                        {rolesLoading ? (
                            <div className="text-sm text-slate-400 dark:text-slate-500">Cargando roles...</div>
                        ) : roles.length === 0 ? (
                            <div className="text-sm text-slate-400 dark:text-slate-500">No hay roles configurados.</div>
                        ) : (
                            <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 pb-4">
                                {roles.map((rol) => <RoleCard key={rol.id} rol={rol} allPermisos={allPermisos} canEdit={canEdit} onSave={updateRolPermisos} />)}
                            </div>
                        )}
                    </section>
                </div>
            )}

            <Modal
                isOpen={isFormOpen}
                onClose={closeForm}
                title={editingUsuario ? `Editar usuario #${editingUsuario.id}` : "Nuevo usuario"}
                footer={
                    <>
                        <Button variant="light" onClick={closeForm}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={handleSave} disabled={isSaving}><CheckIcon className="w-4 h-4" /> {isSaving ? (editingUsuario ? "Guardando cambios..." : "Creando Usuario...") : (editingUsuario ? "Guardar cambios" : "Crear Usuario")}</Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Usuario {!editingUsuario && <span className="text-red-500">*</span>}</span>
                        <input type="text" disabled={!!editingUsuario} className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${formTouched && !form.username.trim() && !editingUsuario ? "border-red-400 bg-red-50" : "border-gray-300"} disabled:bg-gray-100 disabled:text-gray-500 dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100 dark:disabled:bg-slate-700`} value={form.username} onChange={(e) => setForm((prev) => ({ ...prev, username: e.target.value }))} placeholder="Ej: jorge.admin" autoFocus />
                    </label>

                    {!editingUsuario ? (
                        <label className="block">
                            <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Contraseña <span className="text-red-500">*</span></span>
                            <input type="password" className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${formTouched && form.password.trim().length < 6 ? "border-red-400 bg-red-50" : "border-gray-300"} dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100`} value={form.password} onChange={(e) => setForm((prev) => ({ ...prev, password: e.target.value }))} placeholder="Mínimo 6 caracteres" />
                        </label>
                    ) : null}

                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Nombre completo <span className="text-red-500">*</span></span>
                        <input type="text" className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${formTouched && !form.nombreCompleto.trim() ? "border-red-400 bg-red-50" : "border-gray-300"} dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100`} value={form.nombreCompleto} onChange={(e) => setForm((prev) => ({ ...prev, nombreCompleto: e.target.value }))} placeholder="Ej: Jorge Pérez" />
                    </label>

                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Rol <span className="text-red-500">*</span></span>
                        <select className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${formTouched && form.rolId === "" ? "border-red-400 bg-red-50" : "border-gray-300"} dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100`} value={form.rolId} onChange={(e) => setForm((prev) => ({ ...prev, rolId: e.target.value ? Number(e.target.value) : "" }))}>
                            <option value="">Seleccionar rol...</option>
                            {roles.map((rol) => <option key={rol.id} value={rol.id}>{rol.nombre}</option>)}
                        </select>
                    </label>

                    {editingUsuario ? (
                        <label className="inline-flex items-center gap-2 text-sm font-medium text-gray-700 dark:text-slate-200">
                            <input type="checkbox" checked={form.activo} onChange={(e) => setForm((prev) => ({ ...prev, activo: e.target.checked }))} className="w-4 h-4" />
                            Usuario activo
                        </label>
                    ) : null}
                </div>
            </Modal>

            <Modal
                isOpen={isPasswordOpen}
                onClose={closePassword}
                title={passwordUsuario ? `Cambiar contraseña de ${passwordUsuario.username}` : "Cambiar contraseña"}
                size="md"
                footer={
                    <>
                        <Button variant="light" onClick={closePassword}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
                        <Button variant="dark" onClick={handlePasswordSave} disabled={isSaving}><KeyIcon className="w-4 h-4" /> {isSaving ? "Guardando..." : "Actualizar"}</Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Usuario</span>
                        <input
                            type="text"
                            value={passwordUsuario?.username || ""}
                            readOnly
                            autoComplete="username"
                            className="mt-1 block w-full rounded-md border border-gray-300 bg-gray-100 p-2 shadow-sm text-gray-500 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-300"
                        />
                    </label>
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Nueva contraseña <span className="text-red-500">*</span></span>
                        <input type="password" autoComplete="new-password" className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${passwordTouched && nuevaPassword.trim().length < 6 ? "border-red-400 bg-red-50" : "border-gray-300"} dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100`} value={nuevaPassword} onChange={(e) => setNuevaPassword(e.target.value)} placeholder="Mínimo 6 caracteres" autoFocus />
                    </label>
                    <label className="block">
                        <span className="text-sm font-bold text-gray-700 dark:text-slate-200">Confirmar contraseña <span className="text-red-500">*</span></span>
                        <input type="password" autoComplete="new-password" className={`mt-1 block w-full rounded-md border p-2 shadow-sm ${passwordTouched && nuevaPassword !== confirmPassword ? "border-red-400 bg-red-50" : "border-gray-300"} dark:border-slate-600 dark:bg-slate-800 dark:text-slate-100`} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Repetí la contraseña" />
                    </label>
                </div>
            </Modal>
        </main>
    );
}
