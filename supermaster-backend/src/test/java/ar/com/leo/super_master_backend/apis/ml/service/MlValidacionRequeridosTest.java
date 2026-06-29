package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlValidacionRequeridosTest {

    /** Helper: crea un MlAtributoDefDTO mínimo con id y required. */
    private static MlAtributoDefDTO def(String id, boolean required) {
        return new MlAtributoDefDTO(id, id, "STRING", List.of(), List.of(), null, required, false, false, "PRINCIPALES", 1, null, null, null);
    }

    /** Producto base sin atributos guardados. */
    private static Producto productoBase() {
        Producto p = new Producto();
        p.setSku("TEST-001");
        p.setTituloMl("Bicicleta de montaña");
        return p;
    }

    @Test
    void faltantesRequeridos_detectaRequiredAusente_excluyeGtin() {
        Producto p = productoBase(); // sin atributos guardados
        p.getMlAtributos().add(new MlAtributoDTO("SALE_FORMAT", null, "Unidad", false));
        List<MlAtributoDefDTO> defs = List.of(
            def("BICYCLE_TYPE", true), def("SALE_FORMAT", true), def("GTIN", true));
        // BICYCLE_TYPE required y ausente -> falta; SALE_FORMAT presente; GTIN excluido aunque required
        assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).containsExactly("BICYCLE_TYPE");
    }

    @Test
    void faltantesRequeridos_todoPresente_listaVacia() {
        Producto p = productoBase();
        p.getMlAtributos().add(new MlAtributoDTO("BICYCLE_TYPE", null, "MTB", false));
        List<MlAtributoDefDTO> defs = List.of(def("BICYCLE_TYPE", true), def("GTIN", true));
        // BICYCLE_TYPE presente; GTIN excluido -> lista vacía
        assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).isEmpty();
    }

    @Test
    void faltantesRequeridos_noRequiredNoFalta() {
        Producto p = productoBase(); // sin atributos
        List<MlAtributoDefDTO> defs = List.of(def("COLOR", false), def("BICYCLE_TYPE", false));
        // Nada es required -> lista vacía aunque nada esté presente
        assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).isEmpty();
    }

    @Test
    void faltantesRequeridos_atributoNoAplicaCuentaComoAusente() {
        Producto p = productoBase();
        p.getMlAtributos().add(new MlAtributoDTO("BICYCLE_TYPE", null, "", true)); // marcado "No aplica" -> no se envía a ML
        List<MlAtributoDefDTO> defs = List.of(def("BICYCLE_TYPE", true));
        // Aunque esté guardado, al ser "No aplica" no cuenta como presente -> falta
        assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).containsExactly("BICYCLE_TYPE");
    }

    @Test
    void faltantesRequeridos_excluyeEan_igualQueGtin() {
        Producto p = productoBase(); // sin atributos
        List<MlAtributoDefDTO> defs = List.of(def("EAN", true), def("BICYCLE_TYPE", true));
        // EAN excluido; BICYCLE_TYPE ausente -> solo BICYCLE_TYPE falta
        assertThat(MercadoLibreService.faltantesRequeridos(p, defs)).containsExactly("BICYCLE_TYPE");
    }
}
