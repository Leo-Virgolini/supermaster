package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Construye la descripción HTML del producto para Tienda Nube. */
public final class NubeDescripcionBuilder {

    private NubeDescripcionBuilder() {}

    public static String construir(Producto p) {
        List<String> bullets = new ArrayList<>();

        String dimensiones = dimensiones(p);
        if (!dimensiones.isBlank()) bullets.add("Dimensiones: " + dimensiones);
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            bullets.add("Material: " + p.getMaterial().getNombre());
        String aptos = aptos(p);
        if (!aptos.isBlank()) bullets.add("Aptos: " + aptos);
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            bullets.add("Marca: " + p.getMarca().getNombre());

        StringBuilder sb = new StringBuilder("<p><b>CARACTERÍSTICAS</b></p><ul>");
        for (String b : bullets) sb.append("<li>").append(escape(b)).append("</li>");
        sb.append("</ul>");
        return sb.toString();
    }

    private static String dimensiones(Producto p) {
        List<String> partes = new ArrayList<>();
        agregar(partes, "Capacidad", p.getCapacidad());
        agregar(partes, "Largo", p.getLargo());
        agregar(partes, "Ancho", p.getAncho());
        agregar(partes, "Alto", p.getAlto());
        agregar(partes, "Diámetro boca", p.getDiamboca());
        agregar(partes, "Diámetro base", p.getDiambase());
        agregar(partes, "Espesor", p.getEspesor());
        return String.join(", ", partes);
    }

    private static void agregar(List<String> partes, String label, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(label + ": " + valor.trim());
    }

    private static String aptos(Producto p) {
        if (p.getProductosApto() == null) return "";
        return p.getProductosApto().stream()
                .filter(pa -> pa.getApto() != null && pa.getApto().getNombre() != null)
                .map(pa -> pa.getApto().getNombre())
                .collect(Collectors.joining(", "));
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
