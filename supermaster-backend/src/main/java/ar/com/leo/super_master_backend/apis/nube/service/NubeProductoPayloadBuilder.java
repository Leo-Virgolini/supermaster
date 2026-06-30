package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.ml.service.MlItemPayloadBuilder;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el body de POST /products de Tienda Nube para un producto. */
public final class NubeProductoPayloadBuilder {

    private NubeProductoPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado,
                                                List<Long> categoriaIds, SeoGeneradoDTO seo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String nombre = NubeEquipamiento.tituloConSufijo(p.getTituloNube() != null ? p.getTituloNube() : "", p.isEquipamientoGastro());
        payload.put("name", Map.of("es", nombre));
        String descNube = NubeEquipamiento.descripcionConBullet(NubeDescripcionBuilder.construir(p), p.isEquipamientoGastro());
        if (descNube != null && !descNube.isBlank()) payload.put("description", Map.of("es", descNube));
        // Se sube siempre oculto (no visible en la tienda); la publicación se decide manualmente en Nube.
        payload.put("published", false);
        payload.put("free_shipping", false);
        if (p.getMarca() != null && p.getMarca().getNombre() != null && !p.getMarca().getNombre().isBlank())
            payload.put("brand", p.getMarca().getNombre());

        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("sku", p.getSku());
        // Código de barras: el EAN del producto, solo si es un GTIN/EAN válido (mismo criterio que ML).
        if (MlItemPayloadBuilder.esGtinValido(p.getEan())) variant.put("barcode", p.getEan().trim());
        // Precio de lista (tachado) y promocional según haya inflado.
        if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
            variant.put("price", pvpInflado.toPlainString());
            variant.put("promotional_price", pvp.toPlainString());
        } else if (pvp != null) {
            variant.put("price", pvp.toPlainString());
            variant.put("promotional_price", ""); // sin inflado: limpia un promo viejo que haya quedado en Nube
        }
        if (p.getCosto() != null) variant.put("cost", p.getCosto().toPlainString());
        // Peso y dimensiones: valor del request si viene cargado; si no, el default fijo de Nube.
        variant.put("weight", dimOrDefault(p.getNubePeso(), "0.050"));
        variant.put("depth", dimOrDefault(p.getNubeProfundidad(), "8.00"));
        variant.put("width", dimOrDefault(p.getNubeAncho(), "5.00"));
        variant.put("height", dimOrDefault(p.getNubeAlto(), "5.00"));
        variant.put("stock", "0");

        List<Map<String, Object>> variants = new ArrayList<>();
        variants.add(variant);
        payload.put("variants", variants);

        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            payload.put("categories", new ArrayList<>(categoriaIds));
        }

        NubeSeoPayload.aplicar(payload, seo);
        return payload;
    }

    /** Valor de dimensión si viene cargado; si no, el default fijo de Nube. */
    private static String dimOrDefault(String v, String def) {
        return (v != null && !v.isBlank()) ? v.trim() : def;
    }
}
