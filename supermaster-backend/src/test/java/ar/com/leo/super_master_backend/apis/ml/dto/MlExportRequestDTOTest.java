package ar.com.leo.super_master_backend.apis.ml.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlExportRequestDTOTest {

    @Test
    void requestLlevaTitulo() {
        var req = new MlExportRequestDTO(List.of("SKU1"), 1, null, null, null, "Olla acero 24cm");
        assertThat(req.tituloMl()).isEqualTo("Olla acero 24cm");
    }
}
