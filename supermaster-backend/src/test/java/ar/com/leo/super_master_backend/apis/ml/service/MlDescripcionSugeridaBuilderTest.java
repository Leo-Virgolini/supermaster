package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlDescripcionSugeridaBuilderTest {

    @Test
    void componeCaracteristicasSinPrefijoManual() {
        Producto p = new Producto();
        p.setSku("ABC123");
        p.setCapacidad("500 ml");
        Material m = new Material();
        m.setNombre("Plástico");
        p.setMaterial(m);

        String txt = MlDescripcionSugeridaBuilder.construir(p);

        assertThat(txt).contains("CARACTERÍSTICAS");
        assertThat(txt).contains("Material: Plástico");
        assertThat(txt).contains("Capacidad: 500 ml");
        assertThat(txt).contains("SKU: ABC123");
    }
}
