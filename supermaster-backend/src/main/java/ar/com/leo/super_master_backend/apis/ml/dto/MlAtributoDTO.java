package ar.com.leo.super_master_backend.apis.ml.dto;

/** Un atributo de ficha técnica de ML (leído del ítem o enviado al publicar). */
public record MlAtributoDTO(String attributeId, String valueId, String valueName, boolean noAplica) {}
