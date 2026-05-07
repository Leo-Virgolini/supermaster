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
public class Proveedor {

    @JsonProperty("id_proveedor")
    private Integer proveedorId;

    @JsonProperty("proveedor")
    private String proveedor;

    @JsonProperty("tipo_doc")
    private String tipoDoc;

    @JsonProperty("nro_doc")
    private String nroDoc;

    @JsonProperty("provincia")
    private String provincia;

    @JsonProperty("localidad")
    private String localidad;

    @JsonProperty("domicilio")
    private String domicilio;

    @JsonProperty("barrio")
    private String barrio;

    @JsonProperty("cod_postal")
    private String codPostal;

    @JsonProperty("telefono")
    private String telefono;

    @JsonProperty("fax")
    private String fax;

    @JsonProperty("compania_celular")
    private String companiaCelular;

    @JsonProperty("cel")
    private String cel;

    @JsonProperty("persona_contacto")
    private String personaContacto;

    @JsonProperty("email")
    private String email;

    @JsonProperty("pagina_web")
    private String paginaWeb;
}


