package ar.com.leo.super_master_backend.apis.dux.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Subrubro de Dux (GET /subrubros): incluye el rubro padre. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DuxSubrubro(
        @JsonProperty("id_rubro") int idRubro,
        @JsonProperty("id_sub_rubro") int idSubRubro,
        @JsonProperty("rubro") String rubro,
        @JsonProperty("sub_rubro") String subRubro
) {}
