package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Resuelve id_rubro / id_sub_rubro de Dux a partir de la clasificación del producto. */
@Component
public class DuxClasifResolver {

    public record DuxRubro(Integer idRubro, Integer idSubRubro) {}

    public DuxRubro resolver(Producto producto) {
        // Selección de la clasificación fuente: ambas -> general; solo una -> esa.
        if (producto.getClasifGral() != null) {
            return desdeCadena(cadena(producto.getClasifGral(), ClasifGral::getPadre, ClasifGral::getIdDux));
        }
        if (producto.getClasifGastro() != null) {
            return desdeCadena(cadena(producto.getClasifGastro(), ClasifGastro::getPadre, ClasifGastro::getIdDux));
        }
        return new DuxRubro(null, null);
    }

    /** Construye la cadena raíz -> ... -> nodo, como lista de idDux. */
    private <T> List<Integer> cadena(T nodo, Function<T, T> getPadre, Function<T, Integer> getIdDux) {
        List<Integer> desdeNodo = new ArrayList<>();
        T actual = nodo;
        while (actual != null) {
            desdeNodo.add(getIdDux.apply(actual));
            actual = getPadre.apply(actual);
        }
        // desdeNodo = [nodo, ..., raiz]; invertir para [raiz, ..., nodo].
        List<Integer> raizANodo = new ArrayList<>(desdeNodo);
        java.util.Collections.reverse(raizANodo);
        return raizANodo;
    }

    private DuxRubro desdeCadena(List<Integer> raizANodo) {
        Integer idRubro = raizANodo.isEmpty() ? null : raizANodo.get(0);
        Integer idSubRubro = raizANodo.size() >= 2 ? raizANodo.get(1) : null;
        return new DuxRubro(idRubro, idSubRubro);
    }
}
