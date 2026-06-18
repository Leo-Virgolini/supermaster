package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/** Aplana una jerarquía padre→hijos a la lista de nombres ordenada raíz→hoja. */
public final class NubeCategoriaRuta {

    private NubeCategoriaRuta() {}

    public static <T> List<String> aplanar(T hoja, Function<T, T> getPadre, Function<T, String> getNombre) {
        LinkedList<String> ruta = new LinkedList<>();
        for (T n = hoja; n != null; n = getPadre.apply(n)) {
            ruta.addFirst(getNombre.apply(n));
        }
        return ruta;
    }
}
