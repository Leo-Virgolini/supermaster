"use client";
import { useState, useMemo } from "react";
import { MegaphoneIcon, ArrowPathIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../components/Table/core/Table";
import Button from "../components/Button/Button";
import SearchInput from "../components/SearchInput/SearchInput";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../context/AuthContext";
import { useCampanias } from "./useCampanias";
import { getColumns } from "./columns";

export default function CampaniasPage() {
	const { hasPermiso } = useAuth();
	const canEdit = hasPermiso("INTEGRACIONES_EDITAR");

	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(() => getInitialPageSize("campanias"));
	const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: true }]);
	const [filters, setFilters] = useState<any>({ search: "" });
	const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

	const { campanias, totalRecords, isLoading, error, pageCount, isSyncing, updateCampania, sincronizar } =
		useCampanias(pageIndex, pageSize, filters, sorting);

	const columns = useMemo(() => getColumns(canEdit), [canEdit]);

	const handleUpdate = (rowIndex: number, columnId: string, value: unknown) => {
		const c = campanias[rowIndex];
		updateCampania(c.id, { [columnId]: value } as any);
	};

	const handleGlobalSearch = (valor: string) => {
		setFilters((prev: any) => ({ ...prev, search: valor }));
		setPageIndex(0);
	};

	return (
		<main className="px-4 py-2 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
			<div className="flex justify-between items-center mb-2">
				<h1 className="text-3xl font-bold text-gray-800 flex items-center gap-2">
					<MegaphoneIcon className="w-8 h-8 text-gray-600" />
					Campañas Tienda Nube
				</h1>
				<Button variant="dark" onClick={() => { void sincronizar(); }} disabled={!canEdit || isSyncing}>
					<ArrowPathIcon className={`w-4 h-4 ${isSyncing ? "animate-spin" : ""}`} />
					{isSyncing ? "Sincronizando..." : "Sincronizar"}
				</Button>
			</div>

			{error ? <ErrorBanner message={error} /> : (
				<div className="flex-1 min-h-0 overflow-hidden flex flex-col">
					<Table
						searchSlot={
							<SearchInput
								placeholder="Buscar campaña..."
								onSearch={(v) => { if (v !== filters.search) handleGlobalSearch(v); }}
								initialValue={filters.search}
								className="w-64"
							/>
						}
						tableId="campanias"
						data={campanias}
						isLoading={isLoading}
						columns={columns}
						globalFilter={filters.search}
						setGlobalFilter={handleGlobalSearch}
						sorting={sorting}
						setSorting={setSorting}
						pageIndex={pageIndex}
						pageSize={pageSize}
						pageCount={pageCount}
						onPageChange={setPageIndex}
						onPageSizeChange={setPageSize}
						totalRecords={totalRecords}
						rowSelection={rowSelection}
						setRowSelection={setRowSelection}
						updateData={handleUpdate}
					/>
				</div>
			)}
		</main>
	);
}
