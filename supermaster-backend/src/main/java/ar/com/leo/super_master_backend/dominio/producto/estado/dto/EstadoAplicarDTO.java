package ar.com.leo.super_master_backend.dominio.producto.estado.dto;

/** Resultado de aplicar cambios de estado, por canal (null = no se pidió ese canal). */
public record EstadoAplicarDTO(CanalAplicado ml, CanalAplicado hogar, CanalAplicado gastro) {
    public record CanalAplicado(boolean ok, String detalle) {}
}
