import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { OrdenCompraCreateDTO, OrdenCompraDTO, OrdenCompraPatchDTO } from "./types";

const API_URL = `${API_BASE_URL}/api/ordenes-compra`;

export const getOrdenesCompraAPI = async (
  page: number,
  size: number,
  filters: Record<string, unknown> = {},
  sort = "id,desc",
) => {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
    sort,
  });

  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      if (Array.isArray(value)) {
        params.append(key, value.join(","));
      } else {
        params.append(key, String(value));
      }
    }
  });

  const response = await fetchAPI(`${API_URL}?${params.toString()}`);
  if (!response.ok) throw new Error("Error al conectar");
  return await response.json();
};

export const getOrdenCompraByIdAPI = async (id: number): Promise<OrdenCompraDTO> => {
  const response = await fetchAPI(`${API_URL}/${id}`);
  if (!response.ok) throw new Error("Error al obtener orden de compra");
  return await response.json();
};

export const createOrdenCompraAPI = async (dto: OrdenCompraCreateDTO): Promise<OrdenCompraDTO> => {
  const response = await fetchAPI(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
    body: JSON.stringify(dto),
  });
  if (!response.ok) throw new Error("Error al crear orden de compra");
  return await response.json();
};

export const updateOrdenCompraAPI = async (id: number, dto: OrdenCompraPatchDTO): Promise<OrdenCompraDTO> => {
  const response = await fetchAPI(`${API_URL}/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
    body: JSON.stringify(dto),
  });
  if (!response.ok) throw new Error("Error al actualizar orden de compra");
  return await response.json();
};

export const deleteOrdenCompraAPI = async (id: number): Promise<boolean> => {
  const response = await fetchAPI(`${API_URL}/${id}`, {
    method: "DELETE",
    headers: { "X-Audit-Origin": "TABLE" },
  });
  if (!response.ok) throw new Error("Error al eliminar orden de compra");
  return true;
};

export const enviarOrdenCompraAPI = async (id: number): Promise<OrdenCompraDTO> => {
  const response = await fetchAPI(`${API_URL}/${id}/enviar`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
  });
  if (!response.ok) throw new Error("Error al enviar orden de compra");
  return await response.json();
};

export const registrarRecepcionAPI = async (
  id: number,
  lineas: { lineaId: number; cantidadRecibida: number }[],
): Promise<OrdenCompraDTO> => {
  const response = await fetchAPI(`${API_URL}/${id}/recibir`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Audit-Origin": "FORM" },
    body: JSON.stringify({ lineas }),
  });
  if (!response.ok) throw new Error("Error al registrar recepción");
  return await response.json();
};
