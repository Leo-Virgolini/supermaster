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
        // Sin ids de categoría: el gating cierra en seguro (no se autocompletan GTIN/EAN ni MATERIAL).
        return construir(p, categoryId, price, availableQuantity, pictureIds, familyName, Set.of());
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
        // BRAND: si el usuario lo cargó en la ficha (guardado, con valor o "No aplica"), ese prevalece
        // y se agrega en el loop de guardados; si no hay BRAND guardado, se autocompleta desde la marca.
        // Considera también el "No aplica" como guardado: así NO se autocompleta y se evita BRAND duplicado.
        boolean brandGuardado = p.getMlAtributos().stream()
                .anyMatch(a -> "BRAND".equals(a.getAttributeId()));
        if (!brandGuardado && p.getMarca() != null && p.getMarca().getNombre() != null) {
            attributes.add(Map.of("id", "BRAND", "value_name", p.getMarca().getNombre()));
        }
        // MATERIAL: igual que BRAND, se autocompleta desde el material del producto si no hay uno
        // guardado en la ficha. Gateado por la categoría (solo si declara MATERIAL): a diferencia de
        // BRAND no es universal, y mandarlo a una categoría que no lo acepta haría que ML lo rechace.
        boolean materialGuardado = p.getMlAtributos().stream()
                .anyMatch(a -> "MATERIAL".equals(a.getAttributeId()));
        if (!materialGuardado && categoriaAttrIds.contains("MATERIAL")
                && p.getMaterial() != null && p.getMaterial().getNombre() != null) {
            attributes.add(Map.of("id", "MATERIAL", "value_name", p.getMaterial().getNombre()));
        }
        attributes.add(Map.of("id", "SELLER_SKU", "value_name", p.getSku()));
        // Dimensiones del paquete de envío (ML exige enteros, cm y g). Solo si están las 4.
        if (p.getMlPaqAlto() != null && p.getMlPaqAncho() != null
                && p.getMlPaqLargo() != null && p.getMlPaqPeso() != null) {
            // Mapeo natural 1:1 entre el campo del producto y el atributo de ML.
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
        // Solo se envía si es un GTIN/EAN VÁLIDO (longitud 8/12/13/14 + dígito verificador): un
        // identificador inválido hace que ML rechace TODO el array de atributos (las características).
        if (esGtinValido(p.getEan())) {
            String idIdentificador = categoriaAttrIds.contains("GTIN") ? "GTIN"
                                   : categoriaAttrIds.contains("EAN") ? "EAN" : null;
            if (idIdentificador != null) {
                attributes.add(Map.of("id", idIdentificador, "value_name", p.getEan().trim()));
            }
        }
        // Atributos guardados (formato de venta + características)
        for (ProductoMlAtributo a : p.getMlAtributos()) {
            // Si conocemos los atributos válidos de la categoría, descartamos los guardados que NO
            // declara (quedaron "stale" de otra categoría; ML los rechazaría). idsValidos es un
            // superset de la ficha, así que no descarta ninguno legítimo. Con el set vacío (no
            // disponible) NO se filtra, para no perder características.
            if (!categoriaAttrIds.isEmpty() && !categoriaAttrIds.contains(a.getAttributeId())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getAttributeId());
            if (a.isNoAplica()) {
                // "No aplica": ML lo registra como N/A enviando value_id "-1" y value_name null.
                m.put("value_id", "-1");
                m.put("value_name", null);
                attributes.add(m);
                continue;
            }
            if (a.getValueId() != null && !a.getValueId().isBlank()) m.put("value_id", a.getValueId());
            m.put("value_name", a.getValueName());
            attributes.add(m);
        }
        return attributes;
    }

    /**
     * Valida un código GTIN/EAN: longitud 8, 12, 13 o 14 dígitos y dígito verificador correcto
     * (algoritmo módulo 10 estándar de GS1). ML rechaza identificadores con formato inválido.
     */
    public static boolean esGtinValido(String codigo) {
        if (codigo == null) return false;
        String s = codigo.trim();
        int len = s.length();
        if (!(len == 8 || len == 12 || len == 13 || len == 14)) return false;
        for (int i = 0; i < len; i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        int sum = 0;
        for (int i = 0; i < len - 1; i++) {
            int d = s.charAt(len - 2 - i) - '0'; // desde el dígito de datos más a la derecha
            sum += (i % 2 == 0) ? d * 3 : d;
        }
        int check = (10 - (sum % 10)) % 10;
        return check == (s.charAt(len - 1) - '0');
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
