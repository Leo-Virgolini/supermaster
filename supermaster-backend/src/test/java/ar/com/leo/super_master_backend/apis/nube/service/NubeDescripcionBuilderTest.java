package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NubeDescripcionBuilderTest {

    @Test
    void dimensionesComoSubBullets_yTitulosConFormato() {
        Producto p = new Producto();
        // La unidad la trae el propio valor cargado (no la agrega el builder).
        p.setCapacidad("200 ml"); p.setLargo("30 cm"); p.setAncho("5 cm");
        Material m = new Material(); m.setNombre("Plástico"); p.setMaterial(m);
        Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("<b>CARACTERÍSTICAS</b>");
        // Título de característica: negrita + subrayado + color, y las dimensiones en una sub-lista.
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Dimensiones:</span></u></b><ul>");
        assertThat(html).contains("<li>Capacidad: 200 ml</li>");
        assertThat(html).contains("<li>Largo: 30 cm</li>");
        assertThat(html).contains("<li>Ancho: 5 cm</li>");
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Material:</span></u></b> Plástico");
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Marca:</span></u></b> Tramontina");
    }

    @Test
    void omiteBulletsDeDatosVacios() {
        Producto p = new Producto();
        p.setLargo("30 cm"); // sin material, sin marca, sin aptos

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("<li>Largo: 30 cm</li>");
        assertThat(html).doesNotContain("Material:");
        assertThat(html).doesNotContain("Marca:");
        assertThat(html).doesNotContain("Apto"); // ni "Apto:" ni "Aptos:"
    }
}
