"use client";
import { useState, useMemo } from "react";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeftIcon, MegaphoneIcon } from "@heroicons/react/24/outline";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import Table, { getInitialPageSize } from "../../components/Table/core/Table";
import Button from "../../components/Button/Button";
import { type SortingState } from "@tanstack/react-table";
import { useAuth } from "../../context/AuthContext";
import { useCampaniaDetalle } from "./useCampaniaDetalle";
import { getColumns } from "./columns";

export default function CampaniaDetallePage() {
	const params = useParams();
	const router = useRouter();
	const campaniaId = Number(params.id);
	const { hasPermiso } = useAuth();
	const canEdit = hasPermiso("INTEGRACIONES_EDITAR");

	const [pageIndex, setPageIndex] = useState(0);
	const [pageSize, setPageSize] = useState(() => getInitialPageSize("campania-detalle"));
	const [sorting, setSorting] = useState<SortingState>([{ id: "id", desc: false }]);
	const [rowSelection, setRowSelection] = useState<Record<string, boolean>>({});

	const { campania, productos, totalRecords, isLoading, error, pageCount, updatePrecio } =
		useCampaniaDetalle(campaniaId, pageIndex, pageSize, sorting);

	const columns = useMemo(() => getColumns(canEdit), [canEdit]);

	const handleUpdate = (rowIndex: number, _columnId: string, value: unknown) => {
		const p = productos[rowIndex];
		if (!p) return;
		updatePrecio(p.id, value as number | null);
	};

	return (
		<main className="p-4 bg-gray-50 min-h-0 flex flex-col overflow-hidden">
			<div className="flex items-center gap-3 mb-3">
				<Button variant="light" onClick={() => router.push("/campanias")}>
					<ArrowLeftIcon className="w-4 h-4" /> Volver
				</Button>
				<h1 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
					<MegaphoneIcon className="w-7 h-7 text-gray-600" />
					{campania ? campania.nombre : "Campaña"}
				</h1>
			</div>

			{error ? <ErrorBanner message={error} /> : (
				<div className="flex-1 min-h-0 overflow-hidden flex flex-col">
					<Table
						tableId="campania-detalle"
						data={productos}
						isLoading={isLoading}
						columns={columns}
						globalFilter=""
						setGlobalFilter={() => {}}
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
