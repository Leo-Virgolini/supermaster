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

    @Test
    void incluyeBulletsDeLosDatosPresentes() {
        Producto p = new Producto();
        p.setLargo("30"); p.setAncho("5");
        Material m = new Material(); m.setNombre("Plástico"); p.setMaterial(m);
        Marca ma = new Marca(); ma.setNombre("Tramontina"); p.setMarca(ma);

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("<b>CARACTERÍSTICAS</b>");
        assertThat(html).contains("Largo: 30");
        assertThat(html).contains("Ancho: 5");
        assertThat(html).contains("Material: Plástico");
        assertThat(html).contains("Marca: Tramontina");
    }

    @Test
    void omiteBulletsDeDatosVacios() {
        Producto p = new Producto();
        p.setLargo("30"); // sin material, sin marca, sin aptos

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).contains("Largo: 30");
        assertThat(html).doesNotContain("Material:");
        assertThat(html).doesNotContain("Marca:");
        assertThat(html).doesNotContain("Aptos:");
    }
}
