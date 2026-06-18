package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Resuelve una ruta de nombres a ids de categoría de Tienda Nube, creando los niveles faltantes. */
public final class NubeCategoriaResolver {

    private NubeCategoriaResolver() {}

    /**
     * @param creador (parentId, nombre) → id de la categoría creada (POST /categories en prod).
     * @return ids de toda la ruta, en orden raíz→hoja.
     */
    public static List<Long> resolver(NubeCategoriaArbol arbol, List<String> rutaNombres,
                                      BiFunction<Long, String, Long> creador) {
        List<Long> ids = new ArrayList<>();
        Long parentId = null;
        for (String nombre : rutaNombres) {
            Long id = arbol.buscarHijo(parentId, nombre);
            if (id == null) {
                id = creador.apply(parentId, nombre);
                arbol.registrar(id, parentId, nombre);
            }
            ids.add(id);
            parentId = id;
        }
        return ids;
    }
}
