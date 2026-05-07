package ar.com.leo.super_master_backend.dominio.orden_compra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class OrdenCompraPatchDTO {
    private JsonNullable<String> observaciones = JsonNullable.undefined();
    private JsonNullable<List<OrdenCompraLineaCreateDTO>> lineas = JsonNullable.undefined();
}



