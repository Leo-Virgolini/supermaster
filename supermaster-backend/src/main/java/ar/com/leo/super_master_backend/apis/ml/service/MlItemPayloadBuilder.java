package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMlAtributo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Construye el body de POST /items de Mercado Libre (sitio MLA). */
public final class MlItemPayloadBuilder {

    private MlItemPayloadBuilder() {}

    public static Map<String, Object> construir(Producto p, String categoryId, BigDecimal price,
                                                int availableQuantity, List<String> pictureIds, String familyName) {
        return construir(p, categoryId, price, availableQuantity, pictureIds, familyName, Set.of()); // TODO: Task 6 cableará idsValidos de la categoría
    }

    public static Map<String, Object> construir(Producto p, String categoryId, BigDecimal price,
                                                int availableQuantity, List<String> pictureIds, String familyName,
                                                Set<String> categoriaAttrIds) {
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

        payload.put("attributes", construirAtributos(p, categoriaAttrIds));

        Map<String, Object> shipping = new LinkedHashMap<>();
        shipping.put("mode", "me2");
        shipping.put("local_pick_up", true);
        shipping.put("free_shipping", false);
        shipping.put("free_methods", new ArrayList<>());
        payload.put("shipping", shipping);

        List<Map<String, Object>> pictures = new ArrayList<>();
        for (String id : pictureIds) pictures.add(Map.of("id", id));
        payload.put("pictures", pictures);

        return payload;
    }

    /**
     * Atributos del item ML, compartidos por el alta (POST) y la actualización (PUT /items/{id}):
     * condición, marca, SKU, dimensiones del paquete de envío, IVA, impuesto de importación,
     * código universal (EAN/GTIN gateado por los atributos válidos de la categoría) y atributos guardados.
     *
     * @param categoriaAttrIds ids válidos de la categoría ML (Task 6 los cableará; pasar Set.of() si no disponibles).
     */
    public static List<Map<String, Object>> construirAtributos(Producto p, Set<String> categoriaAttrIds) {
        List<Map<String, Object>> attributes = new ArrayList<>();
        attributes.add(Map.of("id", "ITEM_CONDITION", "value_id", "2230284"));
        if (p.getMarca() != null && p.getMarca().getNombre() != null) {
            attributes.add(Map.of("id", "BRAND", "value_name", p.getMarca().getNombre()));
        }
        attributes.add(Map.of("id", "SELLER_SKU", "value_name", p.getSku()));
        // Dimensiones del paquete de envío (ML exige enteros, cm y g). Solo si están las 4.
        if (p.getMlPaqAlto() != null && p.getMlPaqAncho() != null
                && p.getMlPaqLargo() != null && p.getMlPaqPeso() != null) {
            attributes.add(Map.of("id", "SELLER_PACKAGE_HEIGHT", "value_name", cm(p.getMlPaqAlto())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WIDTH",  "value_name", cm(p.getMlPaqAncho())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_LENGTH", "value_name", cm(p.getMlPaqLargo())));
            attributes.add(Map.of("id", "SELLER_PACKAGE_WEIGHT", "value_name", gramos(p.getMlPaqPeso())));
        }
        // IVA: lista cerrada de ML; se mapea el iva del producto. Se omite si no es 0/10.5/21/27.
        Map<String, String> vat = mapearIva(p.getIva());
        if (vat != null) {
            attributes.add(Map.of("id", "VALUE_ADDED_TAX", "value_id", vat.get("id"), "value_name", vat.get("name")));
        }
        // Impuesto de importación: siempre 0 %.
        attributes.add(Map.of("id", "IMPORT_DUTY", "value_id", "49553239", "value_name", "0 %"));
        // Código universal: GTIN si la categoría lo declara, si no EAN. Solo uno; opcional.
        if (p.getEan() != null && !p.getEan().isBlank()) {
            String idIdentificador = categoriaAttrIds.contains("GTIN") ? "GTIN"
                                   : categoriaAttrIds.contains("EAN") ? "EAN" : null;
            if (idIdentificador != null) {
                attributes.add(Map.of("id", idIdentificador, "value_name", p.getEan().trim()));
            }
        }
        // Atributos guardados (formato de venta + características)
        for (ProductoMlAtributo a : p.getMlAtributos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getAttributeId());
            if (a.getValueId() != null && !a.getValueId().isBlank()) m.put("value_id", a.getValueId());
            m.put("value_name", a.getValueName());
            attributes.add(m);
        }
        return attributes;
    }

    private static String cm(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString() + " cm";
    }
    private static String gramos(BigDecimal kg) {
        return kg.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).toPlainString() + " g";
    }
    private static Map<String, String> mapearIva(BigDecimal iva) {
        if (iva == null) return null;
        if (iva.compareTo(new BigDecimal("0"))    == 0) return Map.of("id", "48405907", "name", "0 %");
        if (iva.compareTo(new BigDecimal("10.5")) == 0) return Map.of("id", "48405908", "name", "10.5 %");
        if (iva.compareTo(new BigDecimal("21"))   == 0) return Map.of("id", "48405909", "name", "21 %");
        if (iva.compareTo(new BigDecimal("27"))   == 0) return Map.of("id", "48405910", "name", "27 %");
        return null;
    }
}
