package ar.com.leo.super_master_backend.apis.dux.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stock {

    @JsonProperty("id")
    private int id;

    @JsonProperty("nombre")
    private String nombre;

    @JsonProperty("ctd_disponible")
    private String ctdDisponible;

    @JsonProperty("stock_real")
    private String stockReal;

    @JsonProperty("stock_reservado")
    private String stockReservado;

    @JsonProperty("stock_disponible")
    private String stockDisponible;
}


