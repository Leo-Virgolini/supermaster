package ar.com.leo.super_master_backend.apis.dux.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {

    @JsonProperty("cod_item")
    private String codItem;

    @JsonProperty("item")
    private String item;

    @JsonProperty("codigos_barra")
    private List<String> codigosBarra;

    @JsonProperty("rubro")
    private Rubro rubro;

    @JsonProperty("sub_rubro")
    private SubRubro subRubro;

    @JsonProperty("marca")
    private Marca marca;

    @JsonProperty("proveedor")
    private Proveedor proveedor;

    @JsonProperty("costo")
    private String costo;

    @JsonProperty("porc_iva")
    private String porcIva;

    @JsonProperty("precios")
    private List<Precio> precios;

    @JsonProperty("stock")
    private List<Stock> stock;

    @JsonProperty("id_det_item")
    private String idDetItem;

    @JsonProperty("talle")
    private String talle;

    @JsonProperty("color")
    private String color;

    @JsonProperty("habilitado")
    private String habilitado;

    @JsonProperty("codigo_externo")
    private String codigoExterno;

    @JsonProperty("fecha_creacion")
    private String fechaCreacion;

    @JsonProperty("imagen_url")
    private String imagenUrl;
}


