package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NubeDescripcionBuilder es ahora passthrough: devuelve p.getDescripcionNube() tal cual.
 * La generación de la descripción HTML autoestructurada vive en NubeDescripcionSugeridaBuilder (si existe).
 */
class NubeDescripcionBuilderTest {

    @Test
    void construir_devuelveDescripcionNubeDelProducto() {
        Producto p = new Producto();
        p.setDescripcionNube("<p>Descripción HTML de la tienda Nube.</p>");

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).isEqualTo("<p>Descripción HTML de la tienda Nube.</p>");
    }

    @Test
    void construir_devuelveNull_siDescripcionNubeEsNull() {
        Producto p = new Producto();
        // descripcionNube no seteado -> null

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).isNull();
    }

    @Test
    void construir_devuelveStringVacio_siDescripcionNubeEsVacia() {
        Producto p = new Producto();
        p.setDescripcionNube("");

        String html = NubeDescripcionBuilder.construir(p);

        assertThat(html).isEmpty();
    }

    @Test
    void construir_preservaElContenidoHtmlTalCual() {
        Producto p = new Producto();
        String html = "<p><b>Marca:</b> Tramontina</p><ul><li>Material: Acero</li></ul>";
        p.setDescripcionNube(html);

        String resultado = NubeDescripcionBuilder.construir(p);

        assertThat(resultado).isEqualTo(html);
    }
}
