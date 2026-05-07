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
public class FacturaDux {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("id_cliente")
    private Long idCliente;

    @JsonProperty("id_vendedor")
    private Long idVendedor;

    @JsonProperty("nro_pto_vta")
    private String nroPtoVta;

    @JsonProperty("tipo_comp")
    private String tipoComp;

    @JsonProperty("letra_comp")
    private String letraComp;

    @JsonProperty("nro_comp")
    private Integer nroComp;

    @JsonProperty("fecha_comp")
    private String fecha;

    @JsonProperty("monto_exento")
    private String montoExento;

    @JsonProperty("monto_gravado")
    private String montoGravado;

    @JsonProperty("monto_iva")
    private String montoIva;

    @JsonProperty("monto_desc")
    private String montoDesc;

    @JsonProperty("total")
    private String total;

    @JsonProperty("apellido_razon_soc")
    private String apellidoRazonSoc;

    @JsonProperty("nombre")
    private String nombre;

    @JsonProperty("cuit")
    private String cuit;

    @JsonProperty("anulada")
    private String anulada;

    @JsonProperty("anulada_boolean")
    private Boolean anuladaBoolean;

    @JsonProperty("fecha_registro")
    private String fechaRegistro;

    @JsonProperty("url_factura")
    private String urlFactura;

    @JsonProperty("detalles")
    private List<FacturaDetalleDux> detalles;

    @JsonProperty("detalles_cobro")
    private List<DetalleCobroDux> detallesCobro;

    @JsonProperty("detalles_factura")
    private List<DetalleFacturaRefDux> detallesFactura;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetalleFacturaRefDux {
        @JsonProperty("numero_punto_de_venta")
        private String numeroPuntoDeVenta;

        @JsonProperty("numero_comprobante")
        private Integer numeroComprobante;

        @JsonProperty("letra_comprobante")
        private String letraComprobante;

        @JsonProperty("tipo_comprobante")
        private String tipoComprobante;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FacturaDetalleDux {

        @JsonProperty("cod_item")
        private String codItem;

        @JsonProperty("item")
        private String item;

        @JsonProperty("ctd")
        private String ctd;

        @JsonProperty("precio_uni")
        private String precioUni;

        @JsonProperty("porc_desc")
        private String porcDesc;

        @JsonProperty("porc_iva")
        private String porcIva;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetalleCobroDux {

        @JsonProperty("numero_punto_de_venta")
        private String numeroPuntoDeVenta;

        @JsonProperty("numero_comprobante")
        private Integer numeroComprobante;

        @JsonProperty("personal")
        private String personal;

        @JsonProperty("caja")
        private String caja;

        @JsonProperty("detalles_mov_cobro")
        private List<DetalleMovCobroDux> detallesMovCobro;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DetalleMovCobroDux {

        @JsonProperty("tipo_de_valor")
        private String tipoDeValor;

        @JsonProperty("referencia")
        private String referencia;

        @JsonProperty("monto")
        private Double monto;
    }
}
