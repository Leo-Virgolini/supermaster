"use client";
import { useState } from "react";
import { toast } from "sonner";
import { ShoppingCartIcon, PlusIcon, TrashIcon, CheckIcon, XMarkIcon } from "@heroicons/react/24/outline";
import { confirmDialog } from "../utils/confirmDialog";
import { type SortingState } from "@tanstack/react-table";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import SearchInput from "../components/SearchInput/SearchInput";
import Button from "../components/Button/Button";
import Modal from "../components/Modal/Modal";
import AsyncSelect from "../components/AsyncSelect/AsyncSelect";
import { useOrdenesCompra } from "./useOrdenesCompra";
import { getColumns } from "./columns";
import { OrdenCompraDTO, OrdenCompraCreateDTO } from "./types";
import { getOrdenesCompraAPI, enviarOrdenCompraAPI, registrarRecepcionAPI } from "./ordenesCompraService";
import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { formatFechaAR } from "../utils/formatDate";
import { useAuth } from "../context/AuthContext";

// ---- Funciones de busqueda para AsyncSelect ----

async function searchProveedores(query: string): Promise<{ id: number; label: string }[]> {
  try {
    const res = await fetchAPI(`${API_BASE_URL}/api/proveedores?page=0&size=10&search=${encodeURIComponent(query)}`);
    const json = await res.json();
    return (json.content || []).map((item: any) => ({ id: item.id, label: item.nombre }));
  } catch { return []; }
}

async function searchProductos(query: string): Promise<{ id: number; label: string }[]> {
  try {
    const res = await fetchAPI(`${API_BASE_URL}/api/productos?page=0&size=10&search=${encodeURIComponent(query)}`);
    const json = await res.json();
    return (json.content || []).map((item: any) => ({
      id: item.id,
      label: `[${item.sku}] ${item.tituloWeb || item.descripcion}`,
    }));
  } catch { return []; }
}

// ---- Estado de linea nueva (formulario de agregar linea) ----

interface LineaForm {
  productoId: number | null;
  productoLabel: string;
  cantidadPedida: number;
  costoUnitario: number;
}

const EMPTY_LINEA: LineaForm = {
  productoId: null,
  productoLabel: "",
  cantidadPedida: 1,
  costoUnitario: 0,
};

// ---- Componente principal ----

export default function OrdenesCompraPage() {
  const { hasPermiso } = useAuth();
  const canEdit = hasPermiso("ORDENES_COMPRA_EDITAR");
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSize, setPageSize] = useState(() => getInitialPageSize("ordenes-compra"));
  const [filters, setFilters] = useState<any>({ search: "" });
  const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
  const [rowSelection, setRowSelection] = useState({});

  const { ordenes, totalRecords, isLoading, error, createOrden, deleteOrden, refresh } =
    useOrdenesCompra(pageIndex, pageSize, filters, sorting);

  const pageCount = totalRecords > 0 ? Math.ceil(totalRecords / pageSize) : 1;
  const selectedIds = Object.keys(rowSelection).map(Number);
  const hasSelection = selectedIds.length > 0;

  const handleExportAll = async () => {
      const sortParam = sorting.length > 0 ? sorting[0].id + "," + (sorting[0].desc ? "desc" : "asc") : "id,asc";
      const res = await getOrdenesCompraAPI(0, 99999, filters, sortParam);
      return res.content;
  };

  // ---- Modal Nueva Orden ----
  const [isNuevaOrdenOpen, setIsNuevaOrdenOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [proveedorId, setProveedorId] = useState<number | null>(null);
  const [proveedorValue, setProveedorValue] = useState<number | null>(null);
  const [observaciones, setObservaciones] = useState("");
  const [lineas, setLineas] = useState<{ productoId: number; productoLabel: string; cantidadPedida: number; costoUnitario: number }[]>([]);
  const [lineaForm, setLineaForm] = useState<LineaForm>({ ...EMPTY_LINEA });

  // ---- Modal Enviar Orden ----
  const [ordenAEnviar, setOrdenAEnviar] = useState<OrdenCompraDTO | null>(null);
  const [isEnviando, setIsEnviando] = useState(false);

  // ---- Modal Recepcion ----
  const [ordenRecepcion, setOrdenRecepcion] = useState<OrdenCompraDTO | null>(null);
  const [lineasRecepcion, setLineasRecepcion] = useState<{ lineaId: number; productoDescripcion?: string; productoSku?: string; cantidadOrdenada: number; cantidadRecibida: number }[]>([]);
  const [isRegistrando, setIsRegistrando] = useState(false);

  // ---- Modal Detalle ----
  const [ordenDetalle, setOrdenDetalle] = useState<OrdenCompraDTO | null>(null);

  // ---- Handlers tabla principal ----

  const handleGlobalSearch = (valor: string) => {
    setFilters((prev: any) => ({ ...prev, search: valor }));
    setPageIndex(0);
  };

  const handleDelete = async () => {
    const ids = selectedIds.map(i => `#${i}`);
    const detalle = ids.length <= 3 ? ids.join(", ") : `${ids.slice(0, 3).join(", ")} y ${ids.length - 3} más`;
    if (!(await confirmDialog({ title: "Eliminar órdenes", message: `¿Eliminar ${selectedIds.length === 1 ? `la orden ${detalle}` : `${selectedIds.length} órdenes (${detalle})`}? Solo se pueden eliminar las que estén en estado BORRADOR.`, confirmText: "Eliminar", variant: "danger" }))) return;
    try {
      await deleteOrden(selectedIds);
      setRowSelection({});
    } catch (e) { /* hook already toasts */ }
  };

  // ---- Handlers Nueva Orden ----

  const handleAgregarLinea = () => {
    if (!lineaForm.productoId) {
      toast.warning("Seleccione un producto.");
      return;
    }
    if (lineaForm.cantidadPedida <= 0) {
      toast.warning("La cantidad debe ser mayor a 0.");
      return;
    }
    if (lineaForm.costoUnitario < 0) {
      toast.warning("El costo unitario no puede ser negativo.");
      return;
    }
    setLineas((prev) => [...prev, {
      productoId: lineaForm.productoId!,
      productoLabel: lineaForm.productoLabel,
      cantidadPedida: lineaForm.cantidadPedida,
      costoUnitario: lineaForm.costoUnitario,
    }]);
    setLineaForm({ ...EMPTY_LINEA });
  };

  const handleQuitarLinea = (index: number) => {
    setLineas((prev) => prev.filter((_, i) => i !== index));
  };

  const handleCrearOrden = async () => {
    if (!proveedorId) {
      toast.warning("Seleccione un proveedor.");
      return;
    }
    if (lineas.length === 0) {
      toast.warning("Agregue al menos una linea.");
      return;
    }
    try {
      setIsSaving(true);
      const createDTO: OrdenCompraCreateDTO = {
        proveedorId,
        observaciones: observaciones || null,
        lineas: lineas.map((l) => ({
          productoId: l.productoId,
          cantidadPedida: l.cantidadPedida,
          costoUnitario: l.costoUnitario,
        })),
      };
      await createOrden(createDTO);
      setIsNuevaOrdenOpen(false);
      resetNuevaOrdenForm();
    } catch (e: any) {
      toast.error("Error: " + e.message);
    } finally {
      setIsSaving(false);
    }
  };

  const resetNuevaOrdenForm = () => {
    setProveedorId(null);
    setProveedorValue(null);
    setObservaciones("");
    setLineas([]);
    setLineaForm({ ...EMPTY_LINEA });
  };

  // ---- Handlers Enviar ----

  const handleConfirmarEnviar = async () => {
    if (!ordenAEnviar) return;
    try {
      setIsEnviando(true);
      await enviarOrdenCompraAPI(ordenAEnviar.id);
      await refresh();
      setOrdenAEnviar(null);
    } catch (e: any) {
      toast.error("Error al enviar: " + e.message);
    } finally {
      setIsEnviando(false);
    }
  };

  // ---- Handlers Recepcion ----

  const handleAbrirRecepcion = (orden: OrdenCompraDTO) => {
    setOrdenRecepcion(orden);
    setLineasRecepcion(
      (orden.lineas || []).map((l) => ({
        lineaId: l.id,
        productoDescripcion: l.productoDescripcion,
        productoSku: l.productoSku,
        cantidadOrdenada: l.cantidadPedida,
        cantidadRecibida: l.cantidadRecibida ?? 0,
      })),
    );
  };

  const handleCantidadRecibidaChange = (index: number, value: number) => {
    setLineasRecepcion((prev) =>
      prev.map((l, i) => (i === index ? { ...l, cantidadRecibida: value } : l)),
    );
  };

  const handleConfirmarRecepcion = async () => {
    if (!ordenRecepcion) return;
    try {
      setIsRegistrando(true);
      await registrarRecepcionAPI(
        ordenRecepcion.id,
        lineasRecepcion.map((l) => ({ lineaId: l.lineaId, cantidadRecibida: l.cantidadRecibida })),
      );
      await refresh();
      setOrdenRecepcion(null);
    } catch (e: any) {
      toast.error("Error al registrar recepción: " + e.message);
    } finally {
      setIsRegistrando(false);
    }
  };

  // ---- Columnas ----

  const columns = getColumns(
    (orden) => setOrdenAEnviar(orden),
    (orden) => handleAbrirRecepcion(orden),
    (orden) => setOrdenDetalle(orden),
    canEdit,
  );

  // ---- Render ----

  return (
    <main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
      {/* Cabecera */}
      <div className="flex justify-between items-center mb-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
            <ShoppingCartIcon className="w-8 h-8 text-gray-600" />
            Ordenes de Compra
          </h1>
        </div>

        <div className="flex gap-2 items-center">
          {hasSelection && canEdit && (
            <Button variant="danger" onClick={handleDelete}>
              <TrashIcon className="w-4 h-4" />
              Borrar ({selectedIds.length})
            </Button>
          )}
          <Button
            onClick={() => {
              resetNuevaOrdenForm();
              setIsNuevaOrdenOpen(true);
            }}
            variant="dark"
            disabled={!canEdit}
          >
            <PlusIcon className="w-4 h-4" />
            Crear Orden de Compra
          </Button>
        </div>
      </div>

      {/* Tabla */}
      <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
        <Table
                    searchSlot={<SearchInput placeholder="Buscar orden por número o proveedor..." onSearch={(val) => { if (val !== filters.search) handleGlobalSearch(val); }} initialValue={filters.search} className="w-[26rem] max-w-full" />}
          tableId="ordenes-compra"
          data={ordenes}
          isLoading={isLoading}
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
          getActiveFilter={() => undefined}
          onExportAll={handleExportAll}
          exportFilename="ordenes-compra"
        />
      </div>

      {/* ===== MODAL NUEVA ORDEN ===== */}
      <Modal
        isOpen={isNuevaOrdenOpen}
        onClose={() => setIsNuevaOrdenOpen(false)}
        title="Nueva Orden de Compra"
        footer={
          <>
            <Button variant="light" onClick={() => setIsNuevaOrdenOpen(false)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
            <Button variant="dark" onClick={handleCrearOrden} disabled={!canEdit || isSaving || !proveedorId || lineas.length === 0}><CheckIcon className="w-4 h-4" /> {isSaving ? "Creando Orden de Compra..." : "Crear Orden de Compra"}</Button>
          </>
        }
      >
        <div className="flex flex-col gap-5" style={{ minWidth: 520 }}>
          {/* Proveedor */}
          <AsyncSelect
            label={<>Proveedor <span className="text-red-500">*</span></>}
            placeholder="Escribi para buscar proveedor..."
            loadOptions={searchProveedores}
            value={proveedorValue}
            onChange={(id, label) => {
              setProveedorId(id ? Number(id) : null);
              setProveedorValue(id ? Number(id) : null);
            }}
          />

          {/* Observaciones */}
          <label className="block">
            <span className="font-bold text-gray-700 text-sm">Observaciones</span>
            <textarea
              className="w-full border border-gray-300 p-2 rounded mt-1 text-sm resize-none"
              rows={2}
              value={observaciones}
              onChange={(e) => setObservaciones(e.target.value)}
              placeholder="Observaciones opcionales..."
            />
          </label>

          {/* Lineas */}
          <div>
            <span className="font-bold text-gray-700 text-sm block mb-2">Lineas de la orden</span>

            {/* Formulario agregar linea */}
            <div className="border border-gray-200 rounded p-3 bg-gray-50 flex flex-col gap-3 mb-3">
              <AsyncSelect
                label={<>Producto <span className="text-red-500">*</span></>}
                placeholder="Buscar producto por SKU o nombre..."
                loadOptions={searchProductos}
                value={lineaForm.productoId}
                onChange={(id, label) => {
                  setLineaForm((prev) => ({
                    ...prev,
                    productoId: id ? Number(id) : null,
                    productoLabel: label || "",
                  }));
                }}
              />
              <div className="grid grid-cols-2 gap-3">
                <label className="block">
                  <span className="font-bold text-gray-700 text-xs">Cantidad <span className="text-red-500">*</span></span>
                  <input
                    type="number"
                    min={1}
                    className="w-full border border-gray-300 p-2 rounded mt-1 text-sm"
                    value={lineaForm.cantidadPedida}
                    onChange={(e) =>
                      setLineaForm((prev) => ({ ...prev, cantidadPedida: Number(e.target.value) }))
                    }
                  />
                </label>
                <label className="block">
                  <span className="font-bold text-gray-700 text-xs">Costo Unitario <span className="text-red-500">*</span></span>
                  <input
                    type="number"
                    min={0}
                    step="1"
                    className="w-full border border-gray-300 p-2 rounded mt-1 text-sm"
                    value={lineaForm.costoUnitario}
                    onChange={(e) =>
                      setLineaForm((prev) => ({ ...prev, costoUnitario: Number(e.target.value) }))
                    }
                  />
                </label>
              </div>
              <button
                onClick={handleAgregarLinea}
                className="self-start text-xs px-3 py-1.5 rounded bg-blue-600 text-white hover:bg-blue-700 transition font-semibold"
              >
                + Agregar linea
              </button>
            </div>

            {/* Tabla de lineas */}
            {lineas.length > 0 ? (
              <div className="overflow-auto max-h-48 border border-gray-200 rounded">
                <table className="min-w-full text-xs">
                  <thead className="bg-gray-100 sticky top-0">
                    <tr>
                      <th className="p-2 text-left font-semibold text-gray-600">Producto</th>
                      <th className="p-2 text-right font-semibold text-gray-600">Cantidad</th>
                      <th className="p-2 text-right font-semibold text-gray-600">Costo Unit.</th>
                      <th className="p-2 text-right font-semibold text-gray-600">Subtotal</th>
                      <th className="p-2"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {lineas.map((l, i) => (
                      <tr key={i} className="border-t border-gray-100 dark:border-slate-700 hover:bg-blue-50 dark:hover:bg-blue-900/30">
                        <td className="p-2 text-gray-700">{l.productoLabel || l.productoId}</td>
                        <td className="p-2 text-right text-gray-700">{l.cantidadPedida}</td>
                        <td className="p-2 text-right text-gray-700">${l.costoUnitario.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                        <td className="p-2 text-right text-gray-700">${(l.cantidadPedida * l.costoUnitario).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                        <td className="p-2 text-center">
                          <button
                            onClick={() => handleQuitarLinea(i)}
                            className="text-red-500 hover:text-red-700 font-bold text-sm"
                            title="Quitar linea"
                          >
                            x
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-gray-400 italic">No hay lineas. Agrega al menos una.</p>
            )}
          </div>
        </div>
      </Modal>

      {/* ===== MODAL ENVIAR ORDEN ===== */}
      <Modal
        isOpen={!!ordenAEnviar}
        onClose={() => setOrdenAEnviar(null)}
        title="Enviar Orden de Compra"
        footer={
          <>
            <Button variant="light" onClick={() => setOrdenAEnviar(null)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
            <Button variant="dark" onClick={handleConfirmarEnviar} disabled={isEnviando}><CheckIcon className="w-4 h-4" /> {isEnviando ? "Enviando..." : "Confirmar Envío"}</Button>
          </>
        }
      >
        <div className="flex flex-col gap-3">
          <p className="text-gray-700 text-sm">
            Esta por enviar la orden{" "}
            <strong>#{ordenAEnviar?.id}</strong> al proveedor{" "}
            <strong>{ordenAEnviar?.proveedorNombre}</strong>.
          </p>
          <p className="text-gray-500 text-xs">
            Una vez enviada, el estado cambiara a <span className="font-semibold text-blue-600">ENVIADA</span> y no podra editarse.
          </p>
          {ordenAEnviar && ordenAEnviar.lineas && ordenAEnviar.lineas.length > 0 && (
            <div className="border border-gray-200 rounded overflow-auto max-h-40">
              <table className="min-w-full text-xs">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="p-2 text-left text-gray-600">Producto</th>
                    <th className="p-2 text-right text-gray-600">Cantidad</th>
                    <th className="p-2 text-right text-gray-600">Costo Unit.</th>
                  </tr>
                </thead>
                <tbody>
                  {ordenAEnviar.lineas.map((l, i) => (
                    <tr key={i} className="border-t border-gray-100">
                      <td className="p-2 text-gray-700">{l.productoDescripcion || l.productoSku || l.productoId}</td>
                      <td className="p-2 text-right text-gray-700">{l.cantidadPedida}</td>
                      <td className="p-2 text-right text-gray-700">${l.costoUnitario.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Modal>

      {/* ===== MODAL RECEPCION ===== */}
      <Modal
        isOpen={!!ordenRecepcion}
        onClose={() => setOrdenRecepcion(null)}
        title="Registrar Recepción"
        footer={
          <>
            <Button variant="light" onClick={() => setOrdenRecepcion(null)}><XMarkIcon className="w-4 h-4" /> Cancelar</Button>
            <Button variant="dark" onClick={handleConfirmarRecepcion} disabled={isRegistrando}><CheckIcon className="w-4 h-4" /> {isRegistrando ? "Registrando..." : "Confirmar Recepción"}</Button>
          </>
        }
      >
        <div className="flex flex-col gap-3" style={{ minWidth: 480 }}>
          <p className="text-gray-600 text-sm">
            Orden <strong>#{ordenRecepcion?.id}</strong> — Proveedor:{" "}
            <strong>{ordenRecepcion?.proveedorNombre}</strong>
          </p>
          <p className="text-gray-400 text-xs">
            Ingresa la cantidad recibida para cada linea. Deja en 0 si no se recibio el producto.
          </p>
          <div className="overflow-auto max-h-64 border border-gray-200 rounded">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-100 sticky top-0">
                <tr>
                  <th className="p-2 text-left font-semibold text-gray-600">Producto</th>
                  <th className="p-2 text-right font-semibold text-gray-600">Ordenado</th>
                  <th className="p-2 text-right font-semibold text-gray-600">Recibido</th>
                </tr>
              </thead>
              <tbody>
                {lineasRecepcion.map((l, i) => (
                  <tr key={i} className="border-t border-gray-100 dark:border-slate-700 hover:bg-blue-50 dark:hover:bg-blue-900/30">
                    <td className="p-2 text-gray-700">
                      {l.productoSku ? `[${l.productoSku}] ` : ""}
                      {l.productoDescripcion || l.lineaId}
                    </td>
                    <td className="p-2 text-right text-gray-500">{l.cantidadOrdenada}</td>
                    <td className="p-2 text-right">
                      <input
                        type="number"
                        min={0}
                        max={l.cantidadOrdenada}
                        className="w-20 border border-gray-300 rounded p-1 text-right text-sm"
                        value={l.cantidadRecibida}
                        onChange={(e) => handleCantidadRecibidaChange(i, Number(e.target.value))}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </Modal>

      {/* ===== MODAL DETALLE ===== */}
      <Modal
        isOpen={!!ordenDetalle}
        onClose={() => setOrdenDetalle(null)}
        title={`Detalle Orden #${ordenDetalle?.id}`}
        footer={<Button variant="light" onClick={() => setOrdenDetalle(null)}><XMarkIcon className="w-4 h-4" /> Cerrar</Button>}
      >
        {ordenDetalle && (
          <div className="flex flex-col gap-4" style={{ minWidth: 480 }}>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="font-semibold text-gray-600 block">Proveedor</span>
                <span className="text-gray-800">{ordenDetalle.proveedorNombre || "-"}</span>
              </div>
              <div>
                <span className="font-semibold text-gray-600 block">Estado</span>
                <span className="text-gray-800">{ordenDetalle.estado}</span>
              </div>
              <div>
                <span className="font-semibold text-gray-600 block">Fecha Creación</span>
                <span className="text-gray-800">{formatFechaAR(ordenDetalle.fechaCreacion)}</span>
              </div>
              <div>
                <span className="font-semibold text-gray-600 block">Última Modificación</span>
                <span className="text-gray-800">{formatFechaAR(ordenDetalle.fechaModificacion)}</span>
              </div>
              <div className="col-span-2">
                <span className="font-semibold text-gray-600 block">Observaciones</span>
                <span className="text-gray-800">{ordenDetalle.observaciones || "-"}</span>
              </div>
            </div>

            <div>
              <span className="font-semibold text-gray-700 text-sm block mb-2">Lineas</span>
              {ordenDetalle.lineas && ordenDetalle.lineas.length > 0 ? (
                <div className="overflow-auto max-h-[60vh] border border-gray-200 rounded">
                  <table className="min-w-full text-xs">
                    <thead className="bg-gray-100 sticky top-0">
                      <tr>
                        <th className="p-2 text-left font-semibold text-gray-600">SKU</th>
                        <th className="p-2 text-left font-semibold text-gray-600">Producto</th>
                        <th className="p-2 text-right font-semibold text-gray-600">Cantidad</th>
                        <th className="p-2 text-right font-semibold text-gray-600">Costo Unit.</th>
                        <th className="p-2 text-right font-semibold text-gray-600">Recibido</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ordenDetalle.lineas.map((l, i) => (
                        <tr key={i} className="border-t border-gray-100 dark:border-slate-700 hover:bg-blue-50 dark:hover:bg-blue-900/30">
                          <td className="p-2 text-gray-500">{l.productoSku || "-"}</td>
                          <td className="p-2 text-gray-700">{l.productoDescripcion || l.productoId}</td>
                          <td className="p-2 text-right text-gray-700">{l.cantidadPedida}</td>
                          <td className="p-2 text-right text-gray-700">${l.costoUnitario.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</td>
                          <td className="p-2 text-right text-gray-500">{l.cantidadRecibida ?? "-"}</td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot className="bg-gray-50 sticky bottom-0">
                      <tr className="border-t-2 border-gray-300">
                        <td colSpan={2} className="p-2 text-right font-bold text-gray-700">Total</td>
                        <td className="p-2 text-right font-bold text-gray-700">
                          {ordenDetalle.lineas.reduce((sum, l) => sum + l.cantidadPedida, 0)}
                        </td>
                        <td className="p-2 text-right font-bold text-gray-700">
                          ${ordenDetalle.lineas.reduce((sum, l) => sum + l.cantidadPedida * l.costoUnitario, 0).toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </td>
                        <td className="p-2 text-right font-bold text-gray-500">
                          {ordenDetalle.lineas.reduce((sum, l) => sum + (l.cantidadRecibida ?? 0), 0)}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              ) : (
                <p className="text-xs text-gray-400 italic">Sin lineas registradas.</p>
              )}
            </div>
          </div>
        )}
      </Modal>
    </main>
  );
}

