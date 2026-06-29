package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

/** Descripción para ML: passthrough del campo transitorio (lo que ve el usuario es lo que se publica). */
public final class MlDescripcionBuilder {

    private MlDescripcionBuilder() {}

    public static String construir(Producto p) {
        return p.getDescripcionMl();
    }
}
