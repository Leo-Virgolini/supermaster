package ar.com.leo.super_master_backend.dominio.proveedor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProveedorPatchDTO {
    private JsonNullable<String> nombre = JsonNullable.undefined();
    private JsonNullable<String> apodo = JsonNullable.undefined();
    private JsonNullable<String> plazoPago = JsonNullable.undefined();
    private JsonNullable<Boolean> entrega = JsonNullable.undefined();
    private JsonNullable<BigDecimal> financiacionPorcentaje = JsonNullable.undefined();
    private JsonNullable<Integer> leadTimeDias = JsonNullable.undefined();
}



