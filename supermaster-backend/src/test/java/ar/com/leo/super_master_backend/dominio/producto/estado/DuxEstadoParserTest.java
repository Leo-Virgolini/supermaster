package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuxEstadoParserTest {

    @Test
    void itemNuloEsNoPublicado() {
        EstadoCanalDTO dto = DuxEstadoParser.parse(null);
        assertThat(dto.publicado()).isFalse();
        assertThat(dto.estado()).isNull();
        assertThat(dto.error()).isFalse();
    }

    @Test
    void habilitadoS() {
        Item item = new Item();
        item.setHabilitado("S");
        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("habilitado");
    }

    @Test
    void habilitadoN() {
        Item item = new Item();
        item.setHabilitado("n");
        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.publicado()).isTrue();
        assertThat(dto.estado()).isEqualTo("deshabilitado");
    }
}
