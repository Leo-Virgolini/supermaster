"use client";
import { useState, useMemo } from "react";
import { toast } from "sonner";
import { notificar } from "../utils/notificar";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useCanalRegla } from "./useCanalRegla";
import { getCanalReglasAPI } from "./canalReglaService";
import { getColumns } from "./columns";
import { CanalReglaDTO, CanalReglaUpsertDTO, TipoRegla } from "./types";
import { searchMarcas, searchClasifGral, searchClasifGastro, searchTipos } from "../productos/productosService";
import { type SortingState } from "@tanstack/react-table";
import { FunnelIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon, ExclamationTriangleIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { confirmDialog } from "../utils/confirmDialog";
import { useAuth } from "../context/AuthContext";

type FormLabels = {
    canal?: string;
    producto?: string;
    tipo?: string;
    clasifGral?: string;
    clasifGastro?: string;
    marca?: string;
};

export default function CanalReglaPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("CANALES_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("canal-reglas"));
    const [search, setSearch] = useState("");
    const [columnFilters, setColumnFilters] = useState<Record<string, any>>({});
    const [sorting, setSorting] = useState<SortingState>([{ id: "canalNombre", desc: false }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [ayudaAbierta, setAyudaAbierta] = useState(false);
    const [editingItem, setEditingItem] = useState<CanalReglaDTO | null>(null);
    const [formData, setFormData] = useState<Partial<CanalReglaUpsertDTO>>({});
    const [formLabels, setFormLabels] = useState<FormLabels>({});

    const {
        data, totalRecords, pageCount, isLoading, error,
        createRegla, updateRegla, deleteRegla, searchCanales, searchProductos,
    } = useCanalRegla(pageIndex, pageSize, search, columnFilters, sorting);

    const SORT_MAP: Record<string, string> = {
        canalNombre: "canal.nombre",
        productoLabel: "producto.sku",
    };
    const handleExportAll = async () => {
        const rawId = sorting.length > 0 ? sorting[0].id : "id";
        const sortParam = `${SORT_MAP[rawId] || rawId},${sorting.length > 0 && sorting[0].desc ? "desc" : "asc"}`;
        const res = await getCanalReglasAPI(0, 99999, search, columnFilters, sortParam);
        return res.content;
    };

    const handleEdit = (item: CanalReglaDTO) => {
        setEditingItem(item);
        setFormData({
            canalId: item.canalId,
            tipoRegla: item.tipoRegla,
            tag: item.tag ?? null,
            tipoId: item.tipoId ?? null,
            marcaId: item.marcaId ?? null,
            clasifGralId: item.clasifGralId ?? null,
            clasifGastroId: item.clasifGastroId ?? null,
            productoId: item.productoId ?? null,
            tieneEnvio: item.tieneEnvio ?? null,
        });
        setFormLabels({
            canal: item.canalNombre,
            producto: item.productoLabel,
            tipo: item.tipoNombre,
            marca: item.marcaNombre,
            clasifGral: item.clasifGralNombre,
            clasifGastro: item.clasifGastroNombre,
        });
        setIsModalOpen(true);
    };

    const handleCreate = () => {
        setEditingItem(null);
        setFormData({ tipoRegla: "EXCLUIR" });
        setFormLabels({});
        setIsModalOpen(true);
    };

    const handleSave = async () => {
        if (!formData.canalId || !formData.tipoRegla) {
            toast.warning("Completá canal y acción.");
            return;
        }
        setIsSaving(true);
        try {
            if (editingItem) {
                await updateRegla(editingItem.id, formData);
            } else {
                await createRegla(formData as CanalReglaUpsertDTO);
            }
            setIsModalOpen(false);
        } catch (e) {
            notificar.error((e as any)?.message || "Error al guardar regla");
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async () => {
        const ids = Object.keys(rowSelection).map(Number);
        const nombres = ids
            .map((i) => {
                const r = data.find((p) => p.id === i);
                return r ? `${r.canalNombre || r.canalId} (${r.tipoRegla})` : null;
            })
            .filter(Boolean) as string[];
        const detalle = nombres.length <= 3 ? nombres.join(", ") : `${nombres.slice(0, 3).join(", ")} y ${nombres.length - 3} más`;
        if (await confirmDialog({
            title: "Eliminar reglas",
            message: `¿Eliminar ${ids.length === 1 ? `"${detalle}"` : `${ids.length} reglas (${detalle})`}?`,
            confirmText: "Eliminar",
            variant: "danger",
        })) {
            await deleteRegla(ids);
            setRowSelection({});
        }
    };

    const handleColumnFilterChange = (id: string, val: any) => {
        setColumnFilters((prev) => {
            const next = { ...prev };
            if (!val) delete next[id]; else next[id] = val;
            return next;
        });
        setPageIndex(0);
    };

    const columns = useMemo(() => getColumns({ onEdit: handleEdit, canEdit }), [canEdit]);

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                    <FunnelIcon className="w-8 h-8 text-gray-600" />
                    Reglas de Canal
                </h1>
                <div className="flex gap-2">
                    {Object.keys(rowSelection).length > 0 && canEdit && (
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({Object.keys(rowSelection).length})
                        </Button>
                    )}
                    <Button variant="dark" onClick={handleCreate} disabled={!canEdit}>
                        <PlusIcon className="w-4 h-4" />
                        Crear Regla
                    </Button>
                </div>
            </div>

            <div className="mb-3 rounded-lg border border-blue-200 bg-blue-50 dark:border-blue-800/60 dark:bg-blue-900/20">
                <button
                    type="button"
                    onClick={() => setAyudaAbierta((v) => !v)}
                    className="flex w-full items-center justify-between gap-2 px-4 py-2 text-left text-sm font-semibold text-blue-900 dark:text-blue-200"
                >
                    <span className="flex items-center gap-2">
                        <InformationCircleIcon className="h-5 w-5" />
                        Cómo funciona INCLUIR / EXCLUIR
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Las reglas de canal definen <span className="font-semibold">qué productos forman parte del canal</span>.
                            Si un producto no aplica al canal, no se le calculan precios para ese canal.
                        </p>

                        <div className="mb-3 grid gap-2 md:grid-cols-3">
                            <div className="rounded-md border border-blue-200 bg-white/60 p-3 dark:border-blue-800/60 dark:bg-blue-950/40">
                                <div className="mb-1.5 flex items-center gap-2">
                                    <span className="rounded bg-gray-200 px-2 py-0.5 text-xs font-bold text-gray-700 dark:bg-slate-700 dark:text-slate-200">
                                        Sin regla
                                    </span>
                                </div>
                                <p className="text-xs leading-relaxed">
                                    <span className="font-semibold">Todos</span> los productos aplican al canal.
                                </p>
                            </div>
                            <div className="rounded-md border border-blue-200 bg-white/60 p-3 dark:border-blue-800/60 dark:bg-blue-950/40">
                                <div className="mb-1.5 flex items-center gap-2">
                                    <span className="rounded bg-green-100 px-2 py-0.5 text-xs font-bold text-green-800">
                                        INCLUIR
                                    </span>
                                    <span className="text-xs text-blue-700 dark:text-blue-300">+ condición</span>
                                </div>
                                <p className="text-xs leading-relaxed">
                                    Al canal aplican <span className="font-semibold">únicamente</span> los productos que cumplen al menos una regla INCLUIR.
                                    El resto queda afuera.
                                </p>
                            </div>
                            <div className="rounded-md border border-blue-200 bg-white/60 p-3 dark:border-blue-800/60 dark:bg-blue-950/40">
                                <div className="mb-1.5 flex items-center gap-2">
                                    <span className="rounded bg-red-100 px-2 py-0.5 text-xs font-bold text-red-800">
                                        EXCLUIR
                                    </span>
                                    <span className="text-xs text-blue-700 dark:text-blue-300">+ condición</span>
                                </div>
                                <p className="text-xs leading-relaxed">
                                    Aplican todos los productos <span className="font-semibold">excepto</span> los que cumplen una regla EXCLUIR.
                                </p>
                            </div>
                        </div>

                        <div className="mb-3 rounded-md bg-white/50 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-1 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Ejemplo
                            </p>
                            <p className="mb-1">
                                En el canal <span className="font-mono">KT Gastro</span> solo te interesan productos con Tag{" "}
                                <span className="font-mono">MAQUINA</span>:
                            </p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><span className="font-semibold">Sin regla:</span> todo el catálogo genera precios para KT Gastro.</li>
                                <li><span className="font-semibold">INCLUIR Tag=MAQUINA:</span> solo las máquinas tienen precio en KT Gastro; menaje y repuestos quedan fuera del canal.</li>
                                <li><span className="font-semibold">EXCLUIR Marca=Tramontina:</span> todo el catálogo aplica, menos los productos Tramontina.</li>
                            </ul>
                        </div>

                        <div className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <ExclamationTriangleIcon className="mt-0.5 h-4 w-4 shrink-0" />
                            <p>
                                Las condiciones dentro de una regla se combinan con <span className="font-semibold">AND</span>:
                                si ponés Tag=MAQUINA <em>y</em> Marca=X, sólo aplica a productos que son MAQUINA <em>y</em> además de marca X.
                                Para combinaciones alternativas (Marca X <em>o</em> Marca Y) creá reglas separadas.
                            </p>
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                {error ? <ErrorBanner message={error} /> : (
                    <Table
                        tableId="canal-reglas"
                        searchSlot={<SearchInput placeholder="Buscar regla por canal, marca, producto..." onSearch={(val) => { setSearch(val); setPageIndex(0); }} />}
                        data={data}
                        isLoading={isLoading}
                        columns={columns}
                        pageIndex={pageIndex}
                        pageSize={pageSize}
                        pageCount={pageCount}
                        sorting={sorting}
                        globalFilter={search}
                        rowSelection={rowSelection}
                        onPageChange={setPageIndex}
                        onPageSizeChange={setPageSize}
                        totalRecords={totalRecords}
                        setSorting={setSorting}
                        setGlobalFilter={(value) => { setSearch(String(value)); setPageIndex(0); }}
                        setRowSelection={setRowSelection}
                        onColumnFilterChange={handleColumnFilterChange}
                        getActiveFilter={(id) => columnFilters[id]}
                        onExportAll={handleExportAll}
                        exportFilename="canal-regla"
                    />
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={editingItem ? "Editar Regla" : "Nueva Regla"}
                footer={
                    <>
                        <Button variant="light" onClick={() => setIsModalOpen(false)} text="Cancelar">
                            <XMarkIcon className="w-4 h-4" />
                        </Button>
                        <Button variant="dark" onClick={handleSave} disabled={!canEdit || isSaving}>
                            <CheckIcon className="w-4 h-4" />
                            {isSaving ? (editingItem ? "Guardando cambios..." : "Creando Regla...") : (editingItem ? "Guardar cambios" : "Crear Regla")}
                        </Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <AsyncSelect
                        label={<>Canal <span className="text-red-500">*</span></>}
                        placeholder="Buscar Canal..."
                        loadOptions={searchCanales}
                        onChange={(val, label) => {
                            setFormData({ ...formData, canalId: Number(val) });
                            setFormLabels({ ...formLabels, canal: label || undefined });
                        }}
                        displayValue={formData.canalId ? (formLabels.canal || "Canal Seleccionado") : undefined}
                    />

                    <label className="block">
                        <span className="text-sm font-bold">Acción <span className="text-red-500">*</span></span>
                        <select
                            className="w-full border p-2 rounded mt-1 bg-white"
                            value={formData.tipoRegla}
                            onChange={(e) => setFormData({ ...formData, tipoRegla: e.target.value as TipoRegla })}
                        >
                            <option value="EXCLUIR">EXCLUIR (sacar productos del canal)</option>
                            <option value="INCLUIR">INCLUIR (solo estos productos aplican al canal)</option>
                        </select>
                    </label>

                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide pt-1">
                        Condiciones (se combinan con AND — la regla aplica a productos que cumplan todas)
                    </p>

                    <div className="grid grid-cols-2 gap-3">
                        <AsyncSelect
                            label="Tipo"
                            placeholder="Todos los tipos..."
                            loadOptions={async (q) => (await searchTipos(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => {
                                setFormData({ ...formData, tipoId: val ? Number(val) : null });
                                setFormLabels({ ...formLabels, tipo: label || undefined });
                            }}
                            displayValue={formData.tipoId ? (formLabels.tipo || "Tipo seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Marca"
                            placeholder="Todas las marcas..."
                            loadOptions={async (q) => (await searchMarcas(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => {
                                setFormData({ ...formData, marcaId: val ? Number(val) : null });
                                setFormLabels({ ...formLabels, marca: label || undefined });
                            }}
                            displayValue={formData.marcaId ? (formLabels.marca || "Marca seleccionada") : undefined}
                        />
                        <AsyncSelect
                            label="Rubro (Clasif. Gral)"
                            placeholder="Todos los rubros..."
                            loadOptions={async (q) => (await searchClasifGral(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => {
                                setFormData({ ...formData, clasifGralId: val ? Number(val) : null });
                                setFormLabels({ ...formLabels, clasifGral: label || undefined });
                            }}
                            displayValue={formData.clasifGralId ? (formLabels.clasifGral || "Rubro seleccionado") : undefined}
                        />
                        <AsyncSelect
                            label="Gastro (Clasif. Gastro)"
                            placeholder="Todas las categorías..."
                            loadOptions={async (q) => (await searchClasifGastro(q)).map((o: any) => ({ id: o.id, label: o.label }))}
                            onChange={(val, label) => {
                                setFormData({ ...formData, clasifGastroId: val ? Number(val) : null });
                                setFormLabels({ ...formLabels, clasifGastro: label || undefined });
                            }}
                            displayValue={formData.clasifGastroId ? (formLabels.clasifGastro || "Gastro seleccionado") : undefined}
                        />
                    </div>

                    <AsyncSelect
                        label="Producto específico"
                        placeholder="Buscar por SKU o nombre..."
                        loadOptions={searchProductos}
                        onChange={(val, label) => {
                            setFormData({ ...formData, productoId: val ? Number(val) : null });
                            setFormLabels({ ...formLabels, producto: label || undefined });
                        }}
                        displayValue={formData.productoId ? (formLabels.producto || "Producto seleccionado") : undefined}
                    />

                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <label className="block">
                            <span className="text-sm font-bold">Filtro &quot;Tag&quot;</span>
                            <select
                                className="w-full border p-2 rounded mt-1 bg-white text-sm"
                                value={formData.tag ?? ""}
                                onChange={(e) => {
                                    const v = e.target.value;
                                    setFormData({ ...formData, tag: v ? (v as "MAQUINA" | "REPUESTO" | "MENAJE") : null });
                                }}
                            >
                                <option value="">Sin filtro</option>
                                <option value="MAQUINA">Máquina</option>
                                <option value="REPUESTO">Repuesto</option>
                                <option value="MENAJE">Menaje</option>
                            </select>
                        </label>

                        <label className="block">
                            <span className="text-sm font-bold">Filtro &quot;Tiene Envío&quot;</span>
                            <select
                                className="w-full border p-2 rounded mt-1 bg-white text-sm"
                                value={formData.tieneEnvio === true ? "true" : formData.tieneEnvio === false ? "false" : ""}
                                onChange={(e) => {
                                    const v = e.target.value;
                                    setFormData({ ...formData, tieneEnvio: v === "true" ? true : v === "false" ? false : null });
                                }}
                            >
                                <option value="">Sin filtro</option>
                                <option value="true">Sí tiene envío</option>
                                <option value="false">No tiene envío</option>
                            </select>
                        </label>
                    </div>

                </div>
            </Modal>
        </main>
    );
}
