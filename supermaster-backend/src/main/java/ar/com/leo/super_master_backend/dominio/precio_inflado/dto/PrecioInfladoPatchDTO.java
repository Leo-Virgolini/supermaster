package ar.com.leo.super_master_backend.dominio.precio_inflado.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class PrecioInfladoPatchDTO {
    private JsonNullable<String> codigo = JsonNullable.undefined();
    private JsonNullable<TipoPrecioInflado> tipo = JsonNullable.undefined();
    private JsonNullable<BigDecimal> valor = JsonNullable.undefined();
}



