package ar.com.leo.super_master_backend.dominio.canal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class CanalPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<Integer> canalBaseId = JsonNullable.undefined();
}



