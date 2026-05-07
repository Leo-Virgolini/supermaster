package ar.com.leo.super_master_backend.dominio.apto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class AptoPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
}



