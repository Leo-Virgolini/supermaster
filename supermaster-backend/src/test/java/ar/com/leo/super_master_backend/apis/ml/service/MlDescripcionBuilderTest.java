package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlDescripcionBuilderTest {

    @Test
    void construir_incluyeCabeceraYBullets() {
        Producto p = new Producto();
        Marca marca = new Marca(); marca.setNombre("Tramontina");
        Material material = new Material(); material.setNombre("Acero");
        p.setMarca(marca);
        p.setMaterial(material);
        p.setCapacidad("500ml");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).startsWith("CARACTERÍSTICAS");
        assertThat(desc).contains("• Material: Acero");
        assertThat(desc).contains("• Marca: Tramontina");
        assertThat(desc).contains("500ml");
        assertThat(desc).doesNotContain("<"); // sin HTML
    }

    @Test
    void construir_anteponeDescripcionManual_yLuegoCaracteristicas() {
        Producto p = new Producto();
        Marca marca = new Marca(); marca.setNombre("Tramontina");
        p.setMarca(marca);
        p.setDescripcion("Olla ideal para guisos.\nDoble fondo.");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).startsWith("Olla ideal para guisos.\nDoble fondo.");
        assertThat(desc).contains("CARACTERÍSTICAS");
        assertThat(desc.indexOf("Olla ideal")).isLessThan(desc.indexOf("CARACTERÍSTICAS"));
        assertThat(desc).contains("• Marca: Tramontina");
    }

    @Test
    void construir_quitaHtmlDeLaDescripcionManual() {
        Producto p = new Producto();
        p.setDescripcion("Texto <b>negrita</b> y <br> salto");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).startsWith("Texto negrita y  salto");
        assertThat(desc).doesNotContain("<");
    }

    @Test
    void construir_omiteVacios() {
        Producto p = new Producto();
        Marca marca = new Marca(); marca.setNombre("Tramontina");
        p.setMarca(marca);

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).contains("• Marca: Tramontina");
        assertThat(desc).doesNotContain("Material:");
        assertThat(desc).doesNotContain("Dimensiones:");
    }

    @Test
    void incluyeSkuAlFinal_despuesDeCaracteristicas() {
        Producto p = new Producto();
        p.setSku("ABC123");
        Marca marca = new Marca(); marca.setNombre("Tramontina"); p.setMarca(marca);

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).contains("SKU: ABC123");
        assertThat(desc.indexOf("CARACTERÍSTICAS")).isLessThan(desc.indexOf("SKU: ABC123"));
        assertThat(desc).doesNotContain("<");
    }
}
