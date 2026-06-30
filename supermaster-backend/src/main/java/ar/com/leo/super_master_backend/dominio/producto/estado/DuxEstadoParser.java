package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;

/** Parsea un Item de Dux a EstadoCanalDTO (solo estado: Dux no aporta precio/stock comparables). */
public final class DuxEstadoParser {

    private DuxEstadoParser() {}

    public static EstadoCanalDTO parse(Item item) {
        if (item == null) return EstadoCanalDTO.noPublicado();
        boolean habilitado = "S".equalsIgnoreCase(item.getHabilitado());
        return new EstadoCanalDTO(true, habilitado ? "habilitado" : "deshabilitado",
                null, null, null, null, null, false, null);
    }
}
