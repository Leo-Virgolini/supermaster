package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoLibreServiceCrearCoreTest {

    private Producto productoBase() {
        Producto p = new Producto();
        p.setSku("SKU-VAR-1");
        p.setTituloMl("Vaso Cristal Negro");
        p.setCosto(new BigDecimal("1000"));
        p.setIva(new BigDecimal("21"));
        return p;
    }

    @Test
    void crearItemEnMlCore_capturaFamilyIdYFamilyName() {
        ObjectMapper om = new ObjectMapper();
        String respuestaMl = """
            {"id":"MLA123","user_product_id":"MLAU999",
             "family_id":18446744000000000615,"family_name":"Vaso Cristal"}
            """;

        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                productoBase(), om,
                sku -> false,                       // no existe
                sku -> List.of("foto1.jpg"),        // archivos
                filename -> "PIC1",                 // subir imagen -> picId
                "MLA1055",                          // categoryId
                new BigDecimal("2500"),             // precioFinal
                Set.of(),                           // categoriaAttrIds
                cat -> 60,                          // maxTitleLength
                json -> respuestaMl,                // poster (POST /items)
                (itemId, desc) -> "ok");            // posterDescripcion

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.itemId()).isEqualTo("MLA123");
        assertThat(r.mlau()).isEqualTo("MLAU999");
        assertThat(r.familyId()).isEqualTo("18446744000000000615");
        assertThat(r.familyName()).isEqualTo("Vaso Cristal");
    }

    @Test
    void crearItemEnMlCore_sinFamilyEnRespuesta_dejaFamilyNull() {
        ObjectMapper om = new ObjectMapper();
        String respuestaMl = "{\"id\":\"MLA123\",\"user_product_id\":\"MLAU999\"}";

        ResultadoAltaMl r = MercadoLibreService.crearItemEnMlCore(
                productoBase(), om,
                sku -> false,
                sku -> List.of("foto1.jpg"),
                filename -> "PIC1",
                "MLA1055",
                new BigDecimal("2500"),
                Set.of(),
                cat -> 60,
                json -> respuestaMl,
                (itemId, desc) -> "ok");

        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.CREADO);
        assertThat(r.familyId()).isNull();
        assertThat(r.familyName()).isNull();
    }
}
