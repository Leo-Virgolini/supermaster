package ar.com.leo.super_master_backend.dominio.producto.mla.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class MlaPatchDTO {
    private JsonNullable<String> mla = JsonNullable.undefined();
    private JsonNullable<String> mlau = JsonNullable.undefined();
    private JsonNullable<BigDecimal> precioEnvio = JsonNullable.undefined();
    private JsonNullable<BigDecimal> comisionPorcentaje = JsonNullable.undefined();
    private JsonNullable<Integer> topePromocion = JsonNullable.undefined();
}



