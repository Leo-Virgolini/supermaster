package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NubeEquipamientoTest {

    private Producto conGastro(String... nombresDesdeRaiz) {
        // Construye la cadena padre→hijo y asigna la hoja al producto.
        Producto p = new Producto();
        ClasifGastro padre = null;
        for (String nombre : nombresDesdeRaiz) {
            ClasifGastro n = new ClasifGastro();
            n.setNombre(nombre);
            n.setPadre(padre);
            padre = n;
        }
        p.setClasifGastro(padre);
        return p;
    }

    @Test
    void esEquipamiento_hojaGastro() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("EQUIPAMIENTO"))).isTrue();
    }

    @Test
    void esEquipamiento_ancestroGastro() {
        // raíz EQUIPAMIENTO, hoja otra cosa → true (cualquier nivel)
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("EQUIPAMIENTO", "HORNOS"))).isTrue();
    }

    @Test
    void esEquipamiento_caseInsensitive() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("equipamiento"))).isTrue();
    }

    @Test
    void esEquipamiento_fallbackGral() {
        Producto p = new Producto();
        ClasifGral g = new ClasifGral();
        g.setNombre("EQUIPAMIENTO");
        p.setClasifGral(g);
        assertThat(NubeEquipamiento.esEquipamiento(p)).isTrue();
    }

    @Test
    void esEquipamiento_falseSinMatch() {
        assertThat(NubeEquipamiento.esEquipamiento(conGastro("MENAJE", "OLLAS"))).isFalse();
        assertThat(NubeEquipamiento.esEquipamiento(new Producto())).isFalse();
    }

    @Test
    void tituloConSufijo() {
        assertThat(NubeEquipamiento.tituloConSufijo("Olla", true)).isEqualTo("Olla*");
        assertThat(NubeEquipamiento.tituloConSufijo("Olla*", true)).isEqualTo("Olla*"); // idempotente
        assertThat(NubeEquipamiento.tituloConSufijo("Olla", false)).isEqualTo("Olla");  // no aplica
    }

    @Test
    void descripcionConBullet() {
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p>", true))
                .isEqualTo("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>");
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>", true))
                .isEqualTo("<p>x</p><ul><li>ENVIO A COTIZAR</li></ul>"); // idempotente
        assertThat(NubeEquipamiento.descripcionConBullet("<p>x</p>", false)).isEqualTo("<p>x</p>"); // no aplica
        assertThat(NubeEquipamiento.descripcionConBullet(null, true)).isEqualTo("<ul><li>ENVIO A COTIZAR</li></ul>"); // base vacía
    }
}
