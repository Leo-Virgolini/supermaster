package ar.com.leo.super_master_backend.dominio.marca.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class MarcaPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<String> codigoDux = JsonNullable.undefined();
    private JsonNullable<Integer> padreId = JsonNullable.undefined();
}



