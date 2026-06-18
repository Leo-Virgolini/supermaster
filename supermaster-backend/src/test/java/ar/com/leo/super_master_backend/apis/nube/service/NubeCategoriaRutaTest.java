package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NubeCategoriaRutaTest {

    @Test
    void unNivel_soloRaiz() {
        ClasifGral raiz = new ClasifGral();
        raiz.setNombre("Cocina");
        List<String> ruta = NubeCategoriaRuta.aplanar(raiz, ClasifGral::getPadre, ClasifGral::getNombre);
        assertThat(ruta).containsExactly("Cocina");
    }

    @Test
    void variosNiveles_ordenRaizAHoja() {
        ClasifGral raiz = new ClasifGral(); raiz.setNombre("Cocina");
        ClasifGral hijo = new ClasifGral(); hijo.setNombre("Ollas"); hijo.setPadre(raiz);
        ClasifGral nieto = new ClasifGral(); nieto.setNombre("Acero"); nieto.setPadre(hijo);
        List<String> ruta = NubeCategoriaRuta.aplanar(nieto, ClasifGral::getPadre, ClasifGral::getNombre);
        assertThat(ruta).containsExactly("Cocina", "Ollas", "Acero");
    }
}
