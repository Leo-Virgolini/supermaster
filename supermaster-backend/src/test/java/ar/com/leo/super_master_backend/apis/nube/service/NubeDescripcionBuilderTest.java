package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.apto.entity.Apto;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NubeDescripcionBuilderTest {

    private static ProductoApto apto(String nombre) {
        Apto a = new Apto();
        a.setNombre(nombre);
        ProductoApto pa = new ProductoApto();
        pa.setApto(a);
        return pa;
    }

    @Test
    void ordenCaracteristicas_dimensionesYAptosComoSubBullets_yTitulosConFormato() {
        Producto p = new Producto();
        // La unidad la trae el propio valor cargado (no la agrega el builder).
        p.setCapacidad("200 ml"); p.setLargo("30 cm"); p.setAncho("5 cm");
        Material m = new Material(); m.setNombre("Plástico"); p.setMaterial(m);
        Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);
        Set<ProductoApto> aptos = new LinkedHashSet<>();
        aptos.add(apto("HORNO")); aptos.add(apto("FREEZER"));
        p.setProductosApto(aptos);

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("<b>CARACTERÍSTICAS</b>");
        // Títulos de característica: negrita + subrayado + color.
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Marca:</span></u></b> Tramontina");
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Material:</span></u></b> Plástico");
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Dimensiones:</span></u></b><ul>");
        // Aptos como sub-lista: un sub-bullet por apto.
        assertThat(html).contains("<b><u><span style=\"color:#1e40af\">Apto:</span></u></b><ul>");
        assertThat(html).contains("<li>HORNO</li>");
        assertThat(html).contains("<li>FREEZER</li>");

        // Orden de las características: Marca → Material → Dimensiones → Apto.
        assertThat(html.indexOf("Marca:"))
                .isLessThan(html.indexOf("Material:"))
                .isLessThan(html.indexOf("Dimensiones:"));
        assertThat(html.indexOf("Dimensiones:")).isLessThan(html.indexOf("Apto:"));

        // Orden de las dimensiones: medidas primero, capacidad al final.
        assertThat(html.indexOf("Largo: 30 cm")).isLessThan(html.indexOf("Capacidad: 200 ml"));
        assertThat(html.indexOf("Ancho: 5 cm")).isLessThan(html.indexOf("Capacidad: 200 ml"));
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
