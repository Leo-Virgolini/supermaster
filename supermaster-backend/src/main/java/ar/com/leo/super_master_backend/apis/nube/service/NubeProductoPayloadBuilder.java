package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el body de POST /products de Tienda Nube para un producto. */
public final class NubeProductoPayloadBuilder {

    private NubeProductoPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, BigDecimal pvp, BigDecimal pvpInflado, List<Long> categoriaIds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", Map.of("es", p.getTituloNube() != null ? p.getTituloNube() : ""));
        payload.put("description", Map.of("es", NubeDescripcionBuilder.construir(p)));
        payload.put("published", Boolean.TRUE.equals(p.getActivo()));
        payload.put("free_shipping", false);

        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("sku", p.getSku());
        // Precio de lista (tachado) y promocional según haya inflado.
        if (pvpInflado != null && pvp != null && pvpInflado.compareTo(pvp) > 0) {
            variant.put("price", pvpInflado.toPlainString());
            variant.put("promotional_price", pvp.toPlainString());
        } else if (pvp != null) {
            variant.put("price", pvp.toPlainString());
        }
        if (p.getCosto() != null) variant.put("cost", p.getCosto().toPlainString());
        // Peso y dimensiones por defecto (placeholder hasta cargar datos reales de empaque).
        variant.put("weight", "0.050");
        variant.put("depth", "8.00");
        variant.put("width", "5.00");
        variant.put("height", "5.00");
        variant.put("stock", "");

        List<Map<String, Object>> variants = new ArrayList<>();
        variants.add(variant);
        payload.put("variants", variants);

        if (categoriaIds != null && !categoriaIds.isEmpty()) {
            payload.put("categories", new ArrayList<>(categoriaIds));
        }
        return payload;
    }
}
