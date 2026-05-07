package ar.com.leo.super_master_backend.dominio.regla_descuento.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ReglaDescuentoPatchDTO {
    private JsonNullable<Integer> canalId = JsonNullable.undefined();
    private JsonNullable<Integer> catalogoId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGralId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGastroId = JsonNullable.undefined();
    private JsonNullable<BigDecimal> montoMinimo = JsonNullable.undefined();
    private JsonNullable<BigDecimal> descuentoPorcentaje = JsonNullable.undefined();
    private JsonNullable<Integer> prioridad = JsonNullable.undefined();
    private JsonNullable<Boolean> activo = JsonNullable.undefined();
    private JsonNullable<String> descripcion = JsonNullable.undefined();
}



