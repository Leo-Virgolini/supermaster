package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica pura (testeable offline) para matchear clasificaciones del sistema con
 * los rubros/subrubros de Dux por nombre. No accede a red ni a la BD.
 */
public final class ClasifDuxMatcher {

    private ClasifDuxMatcher() {}

    /**
     * Nodo de clasificación a matchear.
     *
     * @param tienePadre  false = nivel 1 (raíz). true = tiene padre.
     * @param padreEsRaiz true si el padre NO tiene a su vez padre (el nodo es exactamente nivel 2).
     */
    public record ClasifNodo(Integer id, String nombre, String nombrePadre, boolean tienePadre, boolean padreEsRaiz) {}

    /**
     * Para cada clasificación que matchea, el id_dux a asignar.
     * <ul>
     *   <li>Nivel 1 (sin padre): match contra {@code rubro}.</li>
     *   <li>Nivel 2 (padre es raíz): match contra {@code sub_rubro} + {@code rubro} del padre.</li>
     *   <li>Nivel 3+ (padre con padre): se ignora.</li>
     * </ul>
     */
    public static Map<Integer, Integer> match(List<ClasifNodo> nodos, List<DuxRubro> rubros, List<DuxSubrubro> subrubros) {
        Map<Integer, Integer> asignaciones = new HashMap<>();
        for (ClasifNodo n : nodos) {
            if (n.id() == null) continue;
            if (!n.tienePadre()) {
                // Nivel 1: nombre == rubro
                for (DuxRubro r : rubros) {
                    if (igual(r.rubro(), n.nombre())) {
                        asignaciones.put(n.id(), r.idRubro());
                        break;
                    }
                }
            } else if (n.padreEsRaiz()) {
                // Nivel 2: nombre == sub_rubro Y nombre del padre == rubro
                for (DuxSubrubro s : subrubros) {
                    if (igual(s.subRubro(), n.nombre()) && igual(s.rubro(), n.nombrePadre())) {
                        asignaciones.put(n.id(), s.idSubRubro());
                        break;
                    }
                }
            }
            // Nivel 3+: ignorar
        }
        return asignaciones;
    }

    private static boolean igual(String a, String b) {
        return normaliza(a).equals(normaliza(b));
    }

    /** trim + minúsculas + colapsar espacios internos. Null-safe. */
    static String normaliza(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
