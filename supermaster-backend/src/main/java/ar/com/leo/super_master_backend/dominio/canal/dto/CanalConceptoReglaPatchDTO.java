package ar.com.leo.super_master_backend.dominio.canal.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class CanalConceptoReglaPatchDTO {
    private JsonNullable<Integer> canalId = JsonNullable.undefined();
    private JsonNullable<Integer> conceptoId = JsonNullable.undefined();
    private JsonNullable<String> tipoRegla = JsonNullable.undefined();
    private JsonNullable<Integer> tipoId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGastroId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGralId = JsonNullable.undefined();
    private JsonNullable<Integer> marcaId = JsonNullable.undefined();
    private JsonNullable<Boolean> esMaquina = JsonNullable.undefined();
    private JsonNullable<Tag> tag = JsonNullable.undefined();
    private JsonNullable<Boolean> tieneEnvio = JsonNullable.undefined();
}



