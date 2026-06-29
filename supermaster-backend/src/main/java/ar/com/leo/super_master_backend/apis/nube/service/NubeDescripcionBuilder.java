package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

/** Descripción para Nube: passthrough del campo transitorio (HTML tal cual lo edita el usuario). */
public final class NubeDescripcionBuilder {

    private NubeDescripcionBuilder() {}

    public static String construir(Producto p) {
        return p.getDescripcionNube();
    }
}
