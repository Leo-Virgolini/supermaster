"use client";
import { useEffect, useState } from "react";
import {
    searchMarcas,
    searchClasifGral,
    searchTipos,
    searchClasifGastro,
    searchProveedores,
    searchOrigenes,
    searchMateriales,
    searchCatalogos,
    searchAptos,
    searchClientes,
} from "../../../app/productos/productosService";

type Props = {
    columnId: string;
    onClose: () => void;
    onFilter: (value: any, labels?: Record<string, string>) => void;
    currentFilters?: any[]; // Opcional: Para mostrar lo que ya estaba marcado
};

export default function ColumnContextMenu({ columnId, onClose, onFilter, currentFilters = [] }: Props) {
    const [options, setOptions] = useState<{ id: any; label: string }[]>([]);
    const [loading, setLoading] = useState(false);

    // ESTADO NUEVO: Acá guardamos los IDs que el usuario va clickeando [1, 5, 20...]
    const [selectedIds, setSelectedIds] = useState<any[]>(Array.isArray(currentFilters) ? currentFilters : []);

    const loaders: any = {
        marca: (q: string) => searchMarcas(q, 9999),
        rubro: (q: string) => searchClasifGral(q, 9999),
        tipo: (q: string) => searchTipos(q, 9999),
        subrubro: (q: string) => searchClasifGastro(q, 9999),
        proveedor: (q: string) => searchProveedores(q, 9999),
        origen: (q: string) => searchOrigenes(q, 9999),
        material: (q: string) => searchMateriales(q, 9999),
        catalogo: (q: string) => searchCatalogos(q, 9999),
        apto: (q: string) => searchAptos(q, 9999),
        cliente: (q: string) => searchClientes(q, 9999),
        activo: async () => [{ id: true, label: "Sí" }, { id: false, label: "No" }],
        esCombo: async () => [{ id: true, label: "Sí" }, { id: false, label: "No" }],
        tagReposicion: async () => [{ id: "PRIO", label: "PRIO" }, { id: "LIQ", label: "LIQ" }],
    };

    useEffect(() => {
        const load = async () => {
            if (loaders[columnId]) {
                setLoading(true);
                try {
                    const response = await loaders[columnId]("");
                    const listaLimpia = Array.isArray(response) ? response : (response.content || []);
                    setOptions(listaLimpia);
                } catch (error) {
                } finally {
                    setLoading(false);
                }
            }
        };
        load();
    }, [columnId]);

    // Función para marcar/desmarcar
    const toggleOption = (id: any) => {
        setSelectedIds(prev => {
            if (prev.includes(id)) {
                return prev.filter(item => item !== id); // Si estaba, lo saco
            } else {
                return [...prev, id]; // Si no estaba, lo agrego
            }
        });
    };

    // --- RENDERIZADO ---

    // 1. Caso Buscador de Texto (Input simple)
    if (!loaders[columnId]) {
        return (
            <div className="bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-600 shadow-xl rounded text-gray-800 dark:text-slate-200 flex flex-col gap-2 p-2 w-48">
                <span className="font-bold text-xs text-gray-500 dark:text-slate-400 uppercase">Filtrar por texto</span>
                <input
                    autoFocus
                    className="border border-gray-300 dark:border-slate-600 rounded p-1 text-sm bg-white dark:bg-slate-700 text-gray-800 dark:text-slate-200 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    placeholder="Contiene..."
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                            onFilter(e.currentTarget.value);
                            onClose();
                        }
                    }}
                />
            </div>
        );
    }

    // 2. Caso Checkboxes (Lista de opciones)
    return (
        <div
            className="w-56 max-h-80 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-600 shadow-xl rounded flex flex-col text-gray-800 dark:text-slate-200"
            onMouseLeave={() => { /* Opcional: podés quitar esto si querés obligar a usar botones */ }}
        >
            <div className="p-2 border-b border-gray-200 dark:border-slate-600 bg-gray-50 dark:bg-slate-700/50 font-bold text-xs uppercase text-gray-500 dark:text-slate-400 flex justify-between items-center">
                <span>{columnId}</span>
                {selectedIds.length > 0 && (
                    <span className="text-blue-600 dark:text-blue-400 text-[10px] cursor-pointer" onClick={() => setSelectedIds([])}>
                        Limpiar
                    </span>
                )}
            </div>

            <div className="overflow-y-auto flex-1 p-1">
                {loading ? (
                    <div className="p-2 text-xs text-gray-400 dark:text-slate-500">Cargando...</div>
                ) : options.length === 0 ? (
                    <div className="p-2 text-xs text-gray-400 dark:text-slate-500">Sin registros</div>
                ) : (
                    options.map((opt) => {
                        const isSelected = selectedIds.includes(opt.id);
                        return (
                            <button
                                key={opt.id}
                                onClick={() => toggleOption(opt.id)}
                                className={`w-full text-left px-2 py-1.5 text-sm rounded flex items-center gap-2 hover:bg-blue-50 dark:hover:bg-blue-900/30 ${isSelected ? 'bg-blue-50 dark:bg-blue-900/20 font-medium' : ''}`}
                            >
                                <div className={`w-4 h-4 border rounded flex items-center justify-center ${isSelected ? 'bg-blue-600 border-blue-600' : 'border-gray-400 dark:border-slate-500'}`}>
                                    {isSelected && <span className="text-white text-[10px]">✓</span>}
                                </div>
                                {opt.label}
                            </button>
                        );
                    })
                )}
            </div>

            {/* BOTONES DE ACCIÓN (Footer del menú) */}
            <div className="p-2 border-t border-gray-200 dark:border-slate-600 flex justify-end gap-2 bg-gray-50 dark:bg-slate-700/50 rounded-b">
                <button
                    onClick={onClose}
                    className="px-2 py-1 text-xs text-gray-600 dark:text-slate-400 hover:text-gray-800 dark:hover:text-slate-200"
                >
                    Cancelar
                </button>
                <button
                    onClick={() => {
                        const labels: Record<string, string> = {};
                        for (const id of selectedIds) {
                            const opt = options.find((o) => o.id === id);
                            if (opt) labels[String(id)] = opt.label;
                        }
                        onFilter(selectedIds, labels);
                        onClose();
                    }}
                    className="px-3 py-1 bg-blue-600 text-white text-xs rounded hover:bg-blue-700 shadow-sm"
                >
                    Aplicar
                </button>
            </div>
        </div>
    );
}