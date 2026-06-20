package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultadoAltaMlTest {

    @Test
    void actualizadoConMlau_llevaItemIdYMlau() {
        ResultadoAltaMl r = ResultadoAltaMl.actualizado("MLA1", "MLAU9");
        assertThat(r.estado()).isEqualTo(ResultadoAltaMl.Estado.ACTUALIZADO);
        assertThat(r.itemId()).isEqualTo("MLA1");
        assertThat(r.mlau()).isEqualTo("MLAU9");
    }

    @Test
    void actualizadoSinMlau_mlauNull() {
        ResultadoAltaMl r = ResultadoAltaMl.actualizado("MLA1");
        assertThat(r.mlau()).isNull();
    }
}
