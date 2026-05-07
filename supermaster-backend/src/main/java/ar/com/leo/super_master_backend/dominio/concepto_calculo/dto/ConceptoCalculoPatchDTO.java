package ar.com.leo.super_master_backend.dominio.concepto_calculo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ConceptoCalculoPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<BigDecimal> porcentaje = JsonNullable.undefined();
    private JsonNullable<String> aplicaSobre = JsonNullable.undefined();
    private JsonNullable<String> descripcion = JsonNullable.undefined();
}



