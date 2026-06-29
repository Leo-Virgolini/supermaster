package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MlDescripcionBuilder es ahora passthrough: devuelve p.getDescripcionMl() tal cual.
 * La generación de la descripción autoestructurada vive en MlDescripcionSugeridaBuilder.
 */
class MlDescripcionBuilderTest {

    @Test
    void construir_devuelveDescripcionMlDelProducto() {
        Producto p = new Producto();
        p.setDescripcionMl("Texto plano de la descripción ML.");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).isEqualTo("Texto plano de la descripción ML.");
    }

    @Test
    void construir_devuelveNull_siDescripcionMlEsNull() {
        Producto p = new Producto();
        // descripcionMl no seteado -> null

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).isNull();
    }

    @Test
    void construir_devuelveStringVacio_siDescripcionMlEsVacia() {
        Producto p = new Producto();
        p.setDescripcionMl("");

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).isEmpty();
    }

    @Test
    void construir_preservaElContenidoTalCual_sinModificaciones() {
        Producto p = new Producto();
        String contenido = "Línea 1\nLínea 2\n• Bullet con <html> y & caracteres especiales";
        p.setDescripcionMl(contenido);

        String desc = MlDescripcionBuilder.construir(p);

        assertThat(desc).isEqualTo(contenido);
    }
}
