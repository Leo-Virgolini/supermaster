package ar.com.leo.super_master_backend.dominio.sector_deposito.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class SectorDepositoPatchDTO {
    private JsonNullable<String> codigo = JsonNullable.undefined();
    private JsonNullable<Integer> idDux = JsonNullable.undefined();
}
