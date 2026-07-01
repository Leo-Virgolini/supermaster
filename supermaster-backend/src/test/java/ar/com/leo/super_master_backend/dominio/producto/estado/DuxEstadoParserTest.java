package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.model.Stock;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

    @Test
    void sumaStockDisponibleDeMultiplesDepositos() {
        Item item = new Item();
        item.setHabilitado("S");

        Stock stock1 = new Stock();
        stock1.setStockDisponible("5");

        Stock stock2 = new Stock();
        stock2.setStockDisponible("-3");

        item.setStock(Arrays.asList(stock1, stock2));

        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.stock()).isEqualTo(2);
    }

    @Test
    void stockNuloDevuelveNull() {
        Item item = new Item();
        item.setHabilitado("S");
        item.setStock(null);

        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.stock()).isNull();
    }

    @Test
    void stockVacioDevuelveNull() {
        Item item = new Item();
        item.setHabilitado("S");
        item.setStock(Arrays.asList());

        EstadoCanalDTO dto = DuxEstadoParser.parse(item);
        assertThat(dto.stock()).isNull();
    }
}
