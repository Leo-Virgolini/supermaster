import ExcelJS from "exceljs";
import { saveAs } from "file-saver";
import { formatFechaAR } from "./formatDate";

type ExportColumn = {
	header: string;
	accessor: string; // key para leer del row
};

const ISO_DATE_TIME_REGEX = /^\d{4}-\d{2}-\d{2}[T\s]\d{2}:\d{2}(:\d{2})?(\.\d+)?([zZ]|[+-]\d{2}:\d{2})?$/;
const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

function formatExportValue(val: unknown) {
	if (val === null || val === undefined) return "";
	if (typeof val === "boolean") return val ? "Sí" : "No";
	if (Array.isArray(val)) return val.join(", ");
	if (typeof val === "string") {
		if (ISO_DATE_TIME_REGEX.test(val.trim())) {
			return formatFechaAR(val.trim());
		}
		if (ISO_DATE_REGEX.test(val.trim())) {
			const d = new Date(`${val.trim()}T00:00:00`);
			if (!isNaN(d.getTime())) {
				return d.toLocaleDateString("es-AR", {
					day: "2-digit",
					month: "2-digit",
					year: "numeric",
				});
			}
		}
	}
	return val;
}

/**
 * Genera un archivo .xlsx formateado y lo descarga.
 */
export async function exportToExcel(
	rows: Record<string, any>[],
	columns: ExportColumn[],
	filename: string
) {
	const wb = new ExcelJS.Workbook();
	const ws = wb.addWorksheet("Datos");

	// Headers
	ws.columns = columns.map((c) => ({
		header: c.header,
		key: c.accessor,
		width: Math.min(
			Math.max(
				c.header.length + 2,
				...rows.slice(0, 100).map((r) => String(r[c.accessor] ?? "").length)
			) + 2,
			50
		),
	}));

	// Estilo del header
	const headerRow = ws.getRow(1);
	headerRow.font = { bold: true, color: { argb: "FFFFFFFF" }, size: 11 };
	headerRow.fill = {
		type: "pattern",
		pattern: "solid",
		fgColor: { argb: "FF334155" },
	};
	headerRow.alignment = { vertical: "middle", horizontal: "center" };
	headerRow.height = 28;
	headerRow.eachCell((cell) => {
		cell.border = {
			bottom: { style: "thin", color: { argb: "FF94A3B8" } },
		};
	});

	// Datos
	rows.forEach((row, i) => {
		const values: Record<string, any> = {};
		columns.forEach((c) => {
			values[c.accessor] = formatExportValue(row[c.accessor]);
		});
		const excelRow = ws.addRow(values);

		if (i % 2 === 1) {
			excelRow.fill = {
				type: "pattern",
				pattern: "solid",
				fgColor: { argb: "FFF8FAFC" },
			};
		}

		excelRow.alignment = { vertical: "middle" };
		excelRow.height = 22;
	});

	// Bordes sutiles
	ws.eachRow((row, rowNumber) => {
		if (rowNumber === 1) return;
		row.eachCell((cell) => {
			cell.border = {
				bottom: { style: "hair", color: { argb: "FFE2E8F0" } },
			};
		});
	});

	// Auto-filtro
	if (rows.length > 0) {
		ws.autoFilter = {
			from: { row: 1, column: 1 },
			to: { row: 1, column: columns.length },
		};
	}

	// Freeze header row
	ws.views = [{ state: "frozen", ySplit: 1 }];

	const buffer = await wb.xlsx.writeBuffer();
	saveAs(new Blob([buffer]), `${filename}.xlsx`);
}

/**
 * Mapeo conocido: columna id → campo "nombre" en los datos del backend.
 * Cuando una columna usa accessorFn (ej: row => row.marcaId),
 * el col.id es "marca" pero el dato legible es "marcaNombre".
 */
const NOMBRE_MAP: Record<string, string> = {
	marca: "marcaNombre",
	tipo: "tipoNombre",
	clasifGral: "clasifGralNombre",
	clasifGastro: "clasifGastroNombre",
	rubro: "clasifGralNombre",
	subrubro: "clasifGastroNombre",
	proveedor: "proveedorNombre",
	origen: "origenNombre",
	material: "materialNombre",
	mla: "mlaNombre",
	canal: "canalNombre",
	catalogo: "catalogoNombre",
	concepto: "conceptoNombre",
};

/** IDs de columnas de UI (checkboxes, botones) que no deben exportarse */
const SKIP_COLUMNS = new Set(["select", "actions", "acciones", "detalle", "detalles"]);

/**
 * Construye las columnas para exportar a partir de las columnas visibles de TanStack Table.
 * Resuelve automáticamente columnas de relación (marca → marcaNombre).
 * Excluye columnas de UI (select, acciones, detalle).
 */
export function buildExportColumns(
	table: any
): ExportColumn[] {
	return table
		.getVisibleLeafColumns()
		.map((col: any) => {
			if (typeof col.columnDef.header !== "string" || SKIP_COLUMNS.has(col.id)) {
				return null;
			}

			const id: string = col.id;
			const accessorKey = col.columnDef.accessorKey;

			// Si tiene accessorKey explícito, usarlo (tiene prioridad)
			if (accessorKey) {
				return {
					header: col.columnDef.header as string,
					accessor: accessorKey as string,
				};
			}

			// Si es una columna de relación conocida (accessorFn), usar el campo "xxxNombre"
			if (NOMBRE_MAP[id]) {
				return {
					header: col.columnDef.header as string,
					accessor: NOMBRE_MAP[id],
				};
			}

			// Si no tiene accessorKey ni mapeo conocido, asumimos que es una columna de UI
			// (botones, expanders, acciones, badges calculados, etc.) y no se exporta.
			return null;
		})
		.filter(Boolean) as ExportColumn[];
}
