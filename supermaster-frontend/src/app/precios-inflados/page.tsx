"use client";
import { useMemo, useState } from "react";
import { ArrowTrendingUpIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon, InformationCircleIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import { confirmDialog } from "../utils/confirmDialog";
import { notificar } from "../utils/notificar";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Modal from "../components/Modal/Modal";
import Button from "../components/Button/Button";
import { useAuth } from "../context/AuthContext";
import { usePreciosInflados } from "./usePreciosInflados";
import { getPreciosInfladosAPI } from "./preciosInfladosService";
import { getColumns } from "./columns";
import { TipoPrecioInflado } from "./types";
import { type SortingState } from "@tanstack/react-table";

const TIPOS: { value: TipoPrecioInflado; label: string; hint: string }[] = [
    { value: "MULTIPLICADOR", label: "Multiplicador", hint: "Multiplica el precio por el valor (ej: 1.10 = +10%)" },
    { value: "DESCUENTO_PORC", label: "Descuento %", hint: "PVP / (1 - valor/100) — ej: valor=30 → PVP / 0.70" },
    { value: "DIVISOR", label: "Divisor", hint: "Divide el precio por el valor (ej: 0.9 ≈ +11%)" },
    { value: "PRECIO_FIJO", label: "Precio Fijo", hint: "Establece un precio fijo en $" },
];

export default function PreciosInfladosPage() {
    const { hasPermiso } = useAuth();
    const canEdit = hasPermiso("PRECIOS_EDITAR");
    const [pageIndex, setPageIndex] = useState(0);
    const [pageSize, setPageSize] = useState(() => getInitialPageSize("precios_inflados"));
    const [search, setSearch] = useState("");
    const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
    const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isSaving, setIsSaving] = useState(false);
    const [formTouched, setFormTouched] = useState(false);

    const [ayudaAbierta, setAyudaAbierta] = useState(false);

    const [codigo, setCodigo] = useState("");
    const [tipo, setTipo] = useState<TipoPrecioInflado>("MULTIPLICADOR");
    const [valor, setValor] = useState<number>(1);

    const {
        data, totalRecords, pageCount, isLoading, error,
        createItem, updateItem, deleteItem
    } = usePreciosInflados(pageIndex, pageSize, search, sorting);
    const columns = useMemo(() => getColumns(canEdit), [canEdit]);

    const handleExportAll = async () => {
        const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
        const res = await getPreciosInfladosAPI(0, 99999, search, sortParam);
        return res.content;
    };

    const handleCreate = async () => {
        setFormTouched(true);
        if (!codigo.trim()) return;
        if (!(valor > 0)) {
            notificar.warning("El valor debe ser mayor a 0.");
            return;
        }
        setIsSaving(true);
        try {
            await createItem({ codigo: codigo.toUpperCase(), tipo, valor });
            setCodigo("");
            setTipo("MULTIPLICADOR");
            setValor(1);
            setFormTouched(false);
            setIsModalOpen(false);
        } catch (e) {
            // hook ya muestra toast
        } finally {
            setIsSaving(false);
        }
    };

    const handleUpdate = async (rowIndex: number, columnId: string, value: unknown) => {
        const item = data[rowIndex];
        try {
            await updateItem(item.id, { [columnId]: value });
        } catch (e) { /* hook already toasts */ }
    };

    const handleDelete = async () => {
        const ids = Object.keys(rowSelection).map(Number);
        const codigos = ids.map(i => data.find(it => it.id === i)?.codigo).filter(Boolean);
        const detalle = codigos.length <= 3 ? codigos.join(", ") : `${codigos.slice(0, 3).join(", ")} y ${codigos.length - 3} más`;
        if (await confirmDialog({ title: "Eliminar reglas", message: `¿Eliminar ${ids.length === 1 ? `"${detalle}"` : `${ids.length} reglas (${detalle})`}?`, confirmText: "Eliminar", variant: "danger" })) {
            await deleteItem(ids);
            setRowSelection({});
        }
    };

    const selectedTipo = TIPOS.find((t) => t.value === tipo);

    return (
        <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
            <div className="flex justify-between items-center mb-3">
                <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
                    <ArrowTrendingUpIcon className="w-8 h-8 text-gray-600" />
                    Precios Inflados
                </h1>
                <div className="flex gap-2">
                    {canEdit && Object.keys(rowSelection).length > 0 && (
                        <Button variant="danger" onClick={handleDelete}>
                            <TrashIcon className="w-4 h-4" />
                            Borrar ({Object.keys(rowSelection).length})
                        </Button>
                    )}
                    <Button variant="dark" onClick={() => setIsModalOpen(true)} disabled={!canEdit}>
                        <PlusIcon className="w-4 h-4" />
                        Crear Precio Inflado
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
                        ¿Para qué sirven los Precios Inflados?
                    </span>
                    <ChevronDownIcon className={`h-4 w-4 transition-transform ${ayudaAbierta ? "rotate-180" : ""}`} />
                </button>
                {ayudaAbierta && (
                    <div className="border-t border-blue-200 px-4 py-3 text-sm text-blue-900 dark:border-blue-800/60 dark:text-blue-100">
                        <p className="mb-3">
                            Generan un <strong>precio &quot;inflado&quot; (precio tachado)</strong>{" "}
                            que se muestra al cliente junto al precio real, simulando un descuento.
                            Útil para campañas de tipo &quot;antes $X, ahora $Y&quot;.
                        </p>

                        <div className="mb-3 rounded-md bg-white/60 p-3 text-xs dark:bg-blue-950/40">
                            <p className="mb-2 font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
                                Ejemplo
                            </p>
                            <p>
                                Producto con PVP <span className="font-mono">$10.000</span>. Le asignás una regla de inflado{" "}
                                <span className="font-mono">MULTIPLICADOR × 1.30</span>. El cliente verá{" "}
                                <span className="font-mono line-through text-slate-500">$13.000</span>{" "}
                                <span className="font-mono font-bold">$10.000</span> (30% off).
                            </p>
                        </div>

                        <div className="mb-3">
                            <p className="mb-1 font-semibold">Tipos disponibles:</p>
                            <ul className="ml-4 list-disc space-y-0.5">
                                <li><strong>MULTIPLICADOR</strong>: precio × valor (1.10 → +10%)</li>
                                <li><strong>DESCUENTO_PORC</strong>: PVP / (1 − valor/100) (valor=30 → PVP / 0.70)</li>
                                <li><strong>DIVISOR</strong>: precio / valor (0.9 → ≈ +11%)</li>
                                <li><strong>PRECIO_FIJO</strong>: precio absoluto en pesos</li>
                            </ul>
                        </div>

                        <p className="mb-3">
                            Las reglas se crean acá y luego se <strong>asignan a producto + canal</strong>{" "}
                            desde el detalle del producto (pestaña &quot;Precios Inflados&quot;). El precio
                            inflado se calcula sobre el PVP final del canal y se muestra como precio tachado
                            en exportaciones e integraciones (TiendaNube, etc.).
                        </p>

                        <div className="rounded-md border border-amber-300 bg-amber-50 p-2.5 text-xs text-amber-900 dark:border-amber-700/60 dark:bg-amber-900/30 dark:text-amber-100">
                            <strong>Importante:</strong> para que un canal aplique los precios inflados de sus productos, debe tener
                            asignado el concepto <span className="font-mono">FLAG_APLICAR_PRECIO_INFLADO</span> en su lista de Conceptos.
                            Sin ese flag, las reglas no se aplican aunque estén configuradas.
                        </div>
                    </div>
                )}
            </div>

            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                {error ? <ErrorBanner message={error} /> : (
                    <Table
                        tableId="precios_inflados"
                        searchSlot={<SearchInput placeholder="Buscar precio inflado por código..." onSearch={(val) => { setSearch(val); setPageIndex(0); }} />}
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
                        updateData={handleUpdate}
                        onExportAll={handleExportAll}
                        exportFilename="precios-inflados"
                    />
                )}
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => { setIsModalOpen(false); setFormTouched(false); }}
                title="Nuevo Precio Inflado"
                footer={
                    <>
                        <Button variant="light" onClick={() => { setIsModalOpen(false); setFormTouched(false); }}>
                            <XMarkIcon className="w-4 h-4" /> Cancelar
                        </Button>
                        <Button variant="dark" onClick={handleCreate} disabled={isSaving}>
                            <CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Precio Inflado..." : "Crear Precio Inflado"}
                        </Button>
                    </>
                }
            >
                <div className="grid gap-4">
                    <label className="block">
                        <span className="text-sm font-bold">Código <span className="text-red-500">*</span></span>
                        <input
                            className={`w-full border p-2 rounded mt-1 font-mono uppercase ${formTouched && !codigo.trim() ? "border-red-400 bg-red-50" : "border-gray-300"}`}
                            maxLength={20}
                            value={codigo}
                            onChange={(e) => setCodigo(e.target.value.toUpperCase())}
                            placeholder="Ej: DOLAR_BLUE"
                            autoFocus
                        />
                        {formTouched && !codigo.trim() && <span className="text-xs text-red-500 mt-1">Campo obligatorio</span>}
                    </label>

                    <label className="block">
                        <span className="text-sm font-bold">Tipo <span className="text-red-500">*</span></span>
                        <select
                            className="w-full border p-2 rounded mt-1 border-gray-300"
                            value={tipo}
                            onChange={(e) => setTipo(e.target.value as TipoPrecioInflado)}
                        >
                            {TIPOS.map((t) => (
                                <option key={t.value} value={t.value}>{t.label}</option>
                            ))}
                        </select>
                        {selectedTipo && (
                            <p className="text-xs text-gray-500 mt-1 italic">{selectedTipo.hint}</p>
                        )}
                    </label>

                    <label className="block">
                        <span className="text-sm font-bold">Valor <span className="text-red-500">*</span></span>
                        <input
                            type="number"
                            step="0.0001"
                            min="0.01"
                            className="w-full border p-2 rounded mt-1 border-gray-300"
                            value={valor}
                            onChange={(e) => setValor(Number(e.target.value))}
                        />
                    </label>
                </div>
            </Modal>
        </main>
    );
}
