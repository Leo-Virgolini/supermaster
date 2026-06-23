package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Construye el body de POST /items de Mercado Libre (sitio MLA). */
public final class MlItemPayloadBuilder {

    private MlItemPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, String categoryId, BigDecimal price,
                                                int availableQuantity, List<String> pictureIds, String familyName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        // Nuevo modelo User Products: se envía family_name (requerido); ML genera el title.
        payload.put("family_name", familyName);
        payload.put("category_id", categoryId);
        payload.put("price", price);
        payload.put("currency_id", "ARS");
        payload.put("available_quantity", availableQuantity);
        payload.put("buying_mode", "buy_it_now");
        payload.put("listing_type_id", "gold_special");
        payload.put("condition", "new");

        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(Map.of("id", "ITEM_CONDITION", "value_id", "2230284"));
        if (p.getMarca() != null && p.getMarca().getNombre() != null) {
            attributes.add(Map.of("id", "BRAND", "value_name", p.getMarca().getNombre()));
        }
        attributes.add(Map.of("id", "SELLER_SKU", "value_name", p.getSku()));
        payload.put("attributes", attributes);

        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("mode", "me2");
        shipping.put("local_pick_up", false);
        shipping.put("free_shipping", false);
        shipping.put("free_methods", new ArrayList<>());
        payload.put("shipping", shipping);

        List<Map<String, Object>> pictures = new ArrayList<>();
        for (String id : pictureIds) pictures.add(Map.of("id", id));
        payload.put("pictures", pictures);

        return payload;
    }
}
