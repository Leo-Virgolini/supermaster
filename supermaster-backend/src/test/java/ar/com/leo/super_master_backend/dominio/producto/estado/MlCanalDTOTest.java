package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.MlCanalDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MlCanalDTOTest {
    @Test
    void exponeTitulo() {
        MlCanalDTO dto = new MlCanalDTO(EstadoCanalDTO.noPublicado(), null, null,
                List.of(), null, null, null, null, null, null, "Olla acero 24cm", null);
        assertThat(dto.titulo()).isEqualTo("Olla acero 24cm");
    }
}
