package ar.com.leo.super_master_backend.apis.dux.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoDux {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("fecha")
    private String fecha;

    @JsonProperty("estado_remito")
    private String estadoRemito;

    @JsonProperty("detalles")
    private List<PedidoDetalleDux> detalles;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PedidoDetalleDux {

        @JsonProperty("cod_item")
        private String codItem;

        @JsonProperty("ctd")
        private String ctd;
    }
}


