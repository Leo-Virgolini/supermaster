package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DuxProcesoResultadoTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void finalizadoSinErrores_esCreado() {
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso("{\"estado\":\"FINALIZADO\"}", om);
        assertThat(e.finalizado()).isTrue();
        assertThat(e.errores()).isEmpty();
        ExportCanalResultDTO r = DuxService.mapearResultadoProceso(e, 1);
        assertThat(r.creados()).isEqualTo(1);
        assertThat(r.errores()).isEmpty();
        assertThat(r.advertencias()).isEmpty();
    }

    @Test
    void finalizadoConErrores_esError() {
        String json = "{\"estado\":\"FINALIZADO\",\"errores\":[\"codigo_marca no encontrado para el producto con cod_item : 4276.\"]}";
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso(json, om);
        assertThat(e.finalizado()).isTrue();
        ExportCanalResultDTO r = DuxService.mapearResultadoProceso(e, 1);
        assertThat(r.creados()).isZero();
        assertThat(r.errores()).containsExactly("codigo_marca no encontrado para el producto con cod_item : 4276.");
    }

    @Test
    void pendiente_noFinalizado() {
        DuxService.EstadoProceso e = DuxService.parsearEstadoProceso("{\"estado\":\"PENDIENTE\"}", om);
        assertThat(e.finalizado()).isFalse();
    }

    @Test
    void sinConfirmar_esAdvertencia() {
        ExportCanalResultDTO r = DuxService.resultadoSinConfirmar(7);
        assertThat(r.creados()).isZero();
        assertThat(r.errores()).isEmpty();
        assertThat(r.advertencias()).containsExactly("encolado sin confirmar (proceso #7)");
    }
}
