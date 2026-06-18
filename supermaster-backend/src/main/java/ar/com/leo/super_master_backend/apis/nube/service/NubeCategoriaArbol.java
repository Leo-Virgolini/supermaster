package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Árbol de categorías de Tienda Nube cacheado en memoria: (padre, nombre) → id. Match case-insensitive. */
public class NubeCategoriaArbol {

    // parentId normalizado (0 = raíz) → (nombre normalizado → categoriaId)
    private final Map<Long, Map<String, Long>> porPadre = new HashMap<>();

    private static long clavePadre(Long parentId) { return parentId == null ? 0L : parentId; }

    private static String claveNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
    }

    /** Id del hijo con ese nombre bajo ese padre, o null si no existe. */
    public Long buscarHijo(Long parentId, String nombre) {
        Map<String, Long> hijos = porPadre.get(clavePadre(parentId));
        return hijos == null ? null : hijos.get(claveNombre(nombre));
    }

    /** Registra una categoría. Idempotente: no pisa una ya registrada. */
    public void registrar(Long id, Long parentId, String nombre) {
        porPadre.computeIfAbsent(clavePadre(parentId), k -> new HashMap<>())
                .putIfAbsent(claveNombre(nombre), id);
    }
}
