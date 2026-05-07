package ar.com.leo.super_master_backend.dominio.material.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class MaterialPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
}



