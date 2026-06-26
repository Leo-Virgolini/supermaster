package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Construye la descripción HTML del producto para Tienda Nube. */
public final class NubeDescripcionBuilder {

    private NubeDescripcionBuilder() {}

    /** Color del título de cada característica (negrita + subrayado). */
    private static final String LABEL_COLOR = "#1e40af";

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder();
        // Descripción manual del usuario primero (escapada a HTML; saltos de línea como <br>).
        String manual = p.getDescripcion();
        if (manual != null && !manual.isBlank()) {
            sb.append("<p>").append(escape(manual.trim()).replace("\n", "<br>")).append("</p>");
        }
        sb.append("<p><b>CARACTERÍSTICAS</b></p><ul>");

        // Orden: Marca, Material, Dimensiones, Apto.
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("<li>").append(label("Marca")).append(" ").append(escape(p.getMarca().getNombre())).append("</li>");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("<li>").append(label("Material")).append(" ").append(escape(p.getMaterial().getNombre())).append("</li>");

        List<String> dims = dimensiones(p);
        if (!dims.isEmpty()) {
            // Dimensiones como sub-lista: un sub-bullet por medida.
            sb.append("<li>").append(label("Dimensiones")).append("<ul>");
            for (String d : dims) sb.append("<li>").append(escape(d)).append("</li>");
            sb.append("</ul></li>");
        }

        List<String> aptos = aptos(p);
        if (!aptos.isEmpty()) {
            // Aptos como sub-lista: un sub-bullet por apto.
            sb.append("<li>").append(label("Apto")).append("<ul>");
            for (String a : aptos) sb.append("<li>").append(escape(a)).append("</li>");
            sb.append("</ul></li>");
        }

        sb.append("</ul>");
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("<p>").append(label("SKU")).append(" ").append(escape(p.getSku().trim())).append("</p>");
        return sb.toString();
    }

    /** Título de característica en negrita + subrayado + color, ej. {@code <b><u><span ...>Material:</span></u></b>}. */
    private static String label(String texto) {
        return "<b><u><span style=\"color:" + LABEL_COLOR + "\">" + escape(texto) + ":</span></u></b>";
    }

    /** Una entrada por dimensión presente: "Capacidad: 200 ml", "Largo: 25 cm", ... (la unidad la trae el valor cargado). */
    private static List<String> dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        // Medidas primero, capacidad al final.
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
                .collect(Collectors.toList());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
