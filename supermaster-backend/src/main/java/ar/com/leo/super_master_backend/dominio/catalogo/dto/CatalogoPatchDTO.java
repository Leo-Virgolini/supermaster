package ar.com.leo.super_master_backend.dominio.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class CatalogoPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<Boolean> exportarConIva = JsonNullable.undefined();
    private JsonNullable<BigDecimal> recargoPorcentaje = JsonNullable.undefined();
}



