package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.apis.dux.dto.DuxRubro;
import ar.com.leo.super_master_backend.apis.dux.dto.DuxSubrubro;
import ar.com.leo.super_master_backend.apis.dux.service.ClasifDuxMatcher.ClasifNodo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClasifDuxMatcherTest {

    private final List<DuxRubro> rubros = List.of(
            new DuxRubro(10, "Hogar"),
            new DuxRubro(20, "Bazar y Cocina")
    );
    // "Vajilla" existe como subrubro bajo dos rubros distintos: hay que desambiguar por el padre.
    private final List<DuxSubrubro> subrubros = List.of(
            new DuxSubrubro(20, 201, "Bazar y Cocina", "Vajilla"),
            new DuxSubrubro(10, 101, "Hogar", "Vajilla")
    );

    @Test
    void nivel1_matcheaRubroPorNombre() {
        var nodos = List.of(new ClasifNodo(1, "Hogar", null, false, false));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).containsEntry(1, 10);
    }

    @Test
    void nivel1_normalizaMayusculasYEspacios() {
        var nodos = List.of(new ClasifNodo(1, "  bazar  y   COCINA ", null, false, false));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).containsEntry(1, 20);
    }

    @Test
    void nivel2_matcheaSubrubroConRubroPadreCorrecto() {
        // "Vajilla" bajo "Bazar y Cocina" → 201 (no 101, que es "Vajilla" bajo "Hogar")
        var nodos = List.of(new ClasifNodo(5, "Vajilla", "Bazar y Cocina", true, true));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).containsEntry(5, 201);
    }

    @Test
    void nivel2_subrubroOkPeroPadreNoCoincide_noAsigna() {
        var nodos = List.of(new ClasifNodo(5, "Vajilla", "Rubro Inexistente", true, true));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).doesNotContainKey(5);
    }

    @Test
    void nivel3_seIgnora() {
        // tienePadre pero el padre NO es raíz → nivel 3+ → no se matchea
        var nodos = List.of(new ClasifNodo(9, "Vajilla", "Bazar y Cocina", true, false));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).doesNotContainKey(9);
    }

    @Test
    void sinCoincidencia_quedaFueraDelMapa() {
        var nodos = List.of(new ClasifNodo(7, "Algo Inexistente", null, false, false));
        assertThat(ClasifDuxMatcher.match(nodos, rubros, subrubros)).isEmpty();
    }
}
