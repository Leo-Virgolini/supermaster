package ar.com.leo.super_master_backend.dominio.clasif_gral.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ClasifGralPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<Integer> idDux = JsonNullable.undefined();
    private JsonNullable<Integer> padreId = JsonNullable.undefined();
}



