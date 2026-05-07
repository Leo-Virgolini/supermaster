import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";

const BASE = `${API_BASE_URL}/api/productos`;

export interface ProductoMargenDTO {
    id: number | null;
    productoId: number;
    margenMinorista: number | null;
    margenMayorista: number | null;
    margenFijoMinorista: number | null;
    margenFijoMayorista: number | null;
    observaciones: string | null;
}

export const getProductoMargenAPI = async (productoId: number): Promise<ProductoMargenDTO | null> => {
    const res = await fetchAPI(`${BASE}/${productoId}/margen`, { allowedStatuses: [404] });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error("Error al obtener margen del producto");
    return res.json();
};

export const updateProductoMargenAPI = async (
    productoId: number,
    dto: Partial<Omit<ProductoMargenDTO, "id" | "productoId">>
): Promise<ProductoMargenDTO> => {
    const res = await fetchAPI(`${BASE}/${productoId}/margen`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error("Error al guardar margen");
    return res.json();
};

export const deleteProductoMargenAPI = async (productoId: number): Promise<void> => {
    const res = await fetchAPI(`${BASE}/${productoId}/margen`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al eliminar margen");
};
