package ar.com.leo.super_master_backend.apis.nube.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Construye el body de POST /products/{id}/images de Tienda Nube (attachment base64). */
public final class NubeImagenPayloadBuilder {

    private NubeImagenPayloadBuilder() {}

    public static Map<String, Object> construir(String filename, String base64, int position) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filename", filename);
        payload.put("attachment", base64);
        payload.put("position", position);
        return payload;
    }
}
