package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaResolverTest {

    @Test
    void todosExisten_devuelveIdsYNoCrea() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(1L, null, "Cocina");
        a.registrar(2L, 1L, "Ollas");
        List<String> creadas = new ArrayList<>();
        BiFunction<Long, String, Long> creador = (p, n) -> { creadas.add(n); return 99L; };

        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas"), creador);

        assertThat(ids).containsExactly(1L, 2L);
        assertThat(creadas).isEmpty();
    }

    @Test
    void caseInsensitive_reusaSinCrear() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(1L, null, "Cocina");
        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("cocina"), (p, n) -> 50L);
        assertThat(ids).containsExactly(1L);
    }

    @Test
    void creaFaltantes_conParentCorrectoYAnidado() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        AtomicLong seq = new AtomicLong(100);
        List<String> trazas = new ArrayList<>();
        BiFunction<Long, String, Long> creador = (p, n) -> { trazas.add(p + ">" + n); return seq.incrementAndGet(); };

        List<Long> ids = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas", "Acero"), creador);

        assertThat(ids).containsExactly(101L, 102L, 103L);
        assertThat(trazas).containsExactly("null>Cocina", "101>Ollas", "102>Acero");
    }

    @Test
    void segundaResolucion_reusaLoCreadoEnLaPrimera() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        AtomicLong seq = new AtomicLong(0);
        BiFunction<Long, String, Long> creador = (p, n) -> seq.incrementAndGet();

        NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas"), creador);
        List<Long> ids2 = NubeCategoriaResolver.resolver(a, List.of("Cocina", "Ollas", "Acero"), creador);

        assertThat(ids2).containsExactly(1L, 2L, 3L); // Cocina=1, Ollas=2 reusados; Acero=3 nuevo
    }
}
