package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.config.NubeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresión del bug del árbol de categorías de Tienda Nube: la URL de paginación viene del
 * header Link ya codificada (fields=id%2Cname%2Cparent). Si se reenvía tal cual, el RestClient
 * la vuelve a codificar (%2C -> %252C) y Nube responde 422 "Invalid fields for this resource".
 * parseLinkNext debe DECODIFICAR la URL para que se codifique una sola vez, igual que la página 1.
 */
class TiendaNubeParseLinkNextTest {

    private TiendaNubeService service() {
        NubeProperties props = new NubeProperties("https://api.tiendanube.com/v1", null, null, 0, null);
        return new TiendaNubeService(null, props, new ObjectMapper(), null);
    }

    @Test
    void decodificaComasDelQuery_evitaDobleCodificacion() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Link", "<https://api.tiendanube.com/v1/999/categories?per_page=200&fields=id%2Cname%2Cparent&page=2>; rel=\"next\"");

        String next = service().parseLinkNext(headers);

        // Relativa al baseUrl y con las comas DECODIFICADAS (literales): el RestClient las
        // recodifica una sola vez. Sin el fix devolvería "...fields=id%2Cname%2Cparent...".
        assertThat(next).isEqualTo("/999/categories?per_page=200&fields=id,name,parent&page=2");
    }

    @Test
    void sinHeaderNext_devuelveNull() {
        assertThat(service().parseLinkNext(new HttpHeaders())).isNull();
    }
}
