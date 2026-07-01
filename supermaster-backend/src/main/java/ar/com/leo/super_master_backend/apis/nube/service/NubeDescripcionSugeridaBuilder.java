package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Compone una descripción SUGERIDA en HTML (características) para Tienda Nube. */
public final class NubeDescripcionSugeridaBuilder {

    private NubeDescripcionSugeridaBuilder() {}

    private static final String LABEL_COLOR = "#1e40af";

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p><b>CARACTERÍSTICAS</b></p><ul>");
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("<li>").append(label("Marca")).append(" ").append(escape(p.getMarca().getNombre())).append("</li>");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("<li>").append(label("Material")).append(" ").append(escape(p.getMaterial().getNombre())).append("</li>");

        List<String> dims = dimensiones(p);
        if (!dims.isEmpty()) {
            sb.append("<li>").append(label("Dimensiones")).append("<ul>");
            for (String d : dims) sb.append("<li>").append(escape(d)).append("</li>");
            sb.append("</ul></li>");
        }

        List<String> aptos = aptos(p);
        if (!aptos.isEmpty()) {
            sb.append("<li>").append(label("Apto")).append("<ul>");
            for (String a : aptos) sb.append("<li>").append(escape(a)).append("</li>");
            sb.append("</ul></li>");
        }

        sb.append("</ul>");
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("<p>").append(label("SKU")).append(" ").append(escape(p.getSku().trim())).append("</p>");
        return sb.toString();
    }

    private static String label(String texto) {
        return "<b><u><span style=\"color:" + LABEL_COLOR + "\">" + escape(texto) + ":</span></u></b>";
    }

    private static List<String> dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        agregar(partes, "Largo", p.getLargo());
        agregar(partes, "Ancho", p.getAncho());
        agregar(partes, "Alto", p.getAlto());
        agregar(partes, "Diámetro boca", p.getDiamboca());
        agregar(partes, "Diámetro base", p.getDiambase());
        agregar(partes, "Espesor", p.getEspesor());
        agregar(partes, "Capacidad", p.getCapacidad());
        return partes;
    }

    private static void agregar(List<String> partes, String label, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(label + ": " + valor.trim());
    }

    private static List<String> aptos(Producto p) {
        if (p.getProductosApto() == null) return List.of();
        return p.getProductosApto().stream()
                .filter(pa -> pa.getApto() != null && pa.getApto().getNombre() != null)
                .map(pa -> pa.getApto().getNombre())
                .sorted(Collator.getInstance(Locale.of("es", "AR")))  // orden alfabético estable (getProductosApto() es un Set)
                .collect(Collectors.toList());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
