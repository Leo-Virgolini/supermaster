package ar.com.leo.super_master_backend.dominio.producto.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProductoMargenPatchDTO {
    private JsonNullable<BigDecimal> margenMinorista = JsonNullable.undefined();
    private JsonNullable<BigDecimal> margenMayorista = JsonNullable.undefined();
    private JsonNullable<BigDecimal> margenFijoMinorista = JsonNullable.undefined();
    private JsonNullable<BigDecimal> margenFijoMayorista = JsonNullable.undefined();
    private JsonNullable<String> observaciones = JsonNullable.undefined();
}



