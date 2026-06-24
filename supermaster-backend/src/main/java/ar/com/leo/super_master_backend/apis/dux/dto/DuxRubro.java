package ar.com.leo.super_master_backend.apis.dux.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Rubro de Dux (GET /rubros). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DuxRubro(
        @JsonProperty("id_rubro") int idRubro,
        @JsonProperty("rubro") String rubro
) {}
