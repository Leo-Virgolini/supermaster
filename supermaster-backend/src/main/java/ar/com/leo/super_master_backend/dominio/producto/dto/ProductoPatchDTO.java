package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class ProductoPatchDTO {
    private JsonNullable<String> sku = JsonNullable.undefined();
    private JsonNullable<String> codExt = JsonNullable.undefined();
    private JsonNullable<String> descripcion = JsonNullable.undefined();
    private JsonNullable<String> tituloWeb = JsonNullable.undefined();
    private JsonNullable<Boolean> esCombo = JsonNullable.undefined();
    private JsonNullable<Integer> uxb = JsonNullable.undefined();
    private JsonNullable<Integer> moq = JsonNullable.undefined();
    private JsonNullable<String> imagenUrl = JsonNullable.undefined();
    private JsonNullable<Integer> stock = JsonNullable.undefined();
    private JsonNullable<Boolean> activo = JsonNullable.undefined();
    private JsonNullable<Integer> marcaId = JsonNullable.undefined();
    private JsonNullable<Integer> origenId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGralId = JsonNullable.undefined();
    private JsonNullable<Integer> clasifGastroId = JsonNullable.undefined();
    private JsonNullable<Integer> tipoId = JsonNullable.undefined();
    private JsonNullable<Integer> proveedorId = JsonNullable.undefined();
    private JsonNullable<Integer> materialId = JsonNullable.undefined();
    private JsonNullable<Integer> mlaId = JsonNullable.undefined();
    private JsonNullable<String> capacidad = JsonNullable.undefined();
    private JsonNullable<String> largo = JsonNullable.undefined();
    private JsonNullable<String> ancho = JsonNullable.undefined();
    private JsonNullable<String> alto = JsonNullable.undefined();
    private JsonNullable<String> diamboca = JsonNullable.undefined();
    private JsonNullable<String> diambase = JsonNullable.undefined();
    private JsonNullable<String> espesor = JsonNullable.undefined();
    private JsonNullable<BigDecimal> costo = JsonNullable.undefined();
    private JsonNullable<BigDecimal> iva = JsonNullable.undefined();
    private JsonNullable<TagReposicion> tagReposicion = JsonNullable.undefined();
    private JsonNullable<Tag> tag = JsonNullable.undefined();
}



