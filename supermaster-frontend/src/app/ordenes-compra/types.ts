export type EstadoOrdenCompra = 'BORRADOR' | 'ENVIADA' | 'RECIBIDA_PARCIAL' | 'COMPLETA' | 'CANCELADA';

export interface OrdenCompraLineaDTO {
  id: number;
  productoId: number;
  productoSku?: string;
  productoDescripcion?: string;
  cantidadPedida: number;
  cantidadRecibida?: number | null;
  costoUnitario: number;
}

export interface OrdenCompraDTO {
  id: number;
  proveedorId: number;
  proveedorNombre?: string;
  estado: EstadoOrdenCompra;
  observaciones?: string | null;
  lineas: OrdenCompraLineaDTO[];
  fechaCreacion: string;
  fechaModificacion?: string;
}

export interface OrdenCompraLineaUpsertDTO {
  productoId: number;
  cantidadPedida: number;
  costoUnitario: number;
}

export interface OrdenCompraCreateDTO {
  proveedorId: number;
  observaciones?: string | null;
  lineas: OrdenCompraLineaUpsertDTO[];
}

export interface OrdenCompraPatchDTO {
  observaciones?: string | null;
  lineas?: OrdenCompraLineaUpsertDTO[];
}
