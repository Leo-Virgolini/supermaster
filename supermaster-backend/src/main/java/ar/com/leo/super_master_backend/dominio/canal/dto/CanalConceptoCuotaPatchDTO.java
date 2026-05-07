package ar.com.leo.super_master_backend.dominio.canal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class CanalConceptoCuotaPatchDTO {
    private JsonNullable<Integer> cuotas = JsonNullable.undefined();
    private JsonNullable<BigDecimal> porcentaje = JsonNullable.undefined();
    private JsonNullable<String> descripcion = JsonNullable.undefined();
}



