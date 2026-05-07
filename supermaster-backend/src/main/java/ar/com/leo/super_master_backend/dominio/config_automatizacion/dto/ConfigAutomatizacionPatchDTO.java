package ar.com.leo.super_master_backend.dominio.config_automatizacion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ConfigAutomatizacionPatchDTO {
    private JsonNullable<String> clave = JsonNullable.undefined();
    private JsonNullable<String> valor = JsonNullable.undefined();
    private JsonNullable<String> descripcion = JsonNullable.undefined();
}



