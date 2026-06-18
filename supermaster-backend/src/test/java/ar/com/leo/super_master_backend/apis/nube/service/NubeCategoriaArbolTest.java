package ar.com.leo.super_master_backend.apis.nube.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaArbolTest {

    @Test
    void buscarHijo_inexistente_devuelveNull() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        assertThat(a.buscarHijo(null, "Cocina")).isNull();
    }

    @Test
    void registrarYBuscar_caseInsensitiveYConTrim() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        assertThat(a.buscarHijo(null, "cocina")).isEqualTo(10L);
        assertThat(a.buscarHijo(null, "  COCINA ")).isEqualTo(10L);
    }

    @Test
    void registrar_distinguePorPadre() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        a.registrar(20L, 10L, "Ollas");
        assertThat(a.buscarHijo(10L, "Ollas")).isEqualTo(20L);
        assertThat(a.buscarHijo(null, "Ollas")).isNull(); // mismo nombre, otro padre
    }

    @Test
    void registrar_noPisaExistente() {
        NubeCategoriaArbol a = new NubeCategoriaArbol();
        a.registrar(10L, null, "Cocina");
        a.registrar(11L, null, "Cocina");
        assertThat(a.buscarHijo(null, "Cocina")).isEqualTo(10L);
    }
}
