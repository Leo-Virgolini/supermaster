package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;

import java.util.ArrayList;
import java.util.List;

/** Construye la descripción en TEXTO PLANO para Mercado Libre (sin HTML; saltos con \n). */
public final class MlDescripcionBuilder {

    private MlDescripcionBuilder() {}

    public static String construir(Producto p) {
        StringBuilder sb = new StringBuilder();
        // Descripción manual del usuario primero (texto plano: se le quitan etiquetas HTML).
        String manual = plano(p.getDescripcion());
        if (!manual.isBlank()) {
            sb.append(manual).append("\n\n");
        }
        sb.append("CARACTERÍSTICAS\n");
        String dimensiones = dimensiones(p);
        if (!dimensiones.isBlank()) sb.append("• Dimensiones: ").append(dimensiones).append("\n");
        if (p.getMaterial() != null && p.getMaterial().getNombre() != null)
            sb.append("• Material: ").append(p.getMaterial().getNombre()).append("\n");
        String aptos = aptos(p);
        if (!aptos.isBlank()) sb.append("• Aptos: ").append(aptos).append("\n");
        if (p.getMarca() != null && p.getMarca().getNombre() != null)
            sb.append("• Marca: ").append(p.getMarca().getNombre()).append("\n");
        if (p.getSku() != null && !p.getSku().isBlank())
            sb.append("SKU: ").append(p.getSku().trim()).append("\n");
        return sb.toString();
    }

    /** Quita etiquetas HTML del texto manual (ML solo acepta texto plano) y hace trim. */
    private static String plano(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", "").trim();
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

    private static void agregar(List<String> partes, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) partes.add(etiqueta + ": " + valor.trim());
    }

    private static String aptos(Producto p) {
        if (p.getProductosApto() == null) return "";
        List<String> nombres = new ArrayList<>();
        for (ProductoApto pa : p.getProductosApto()) {
            if (pa.getApto() != null && pa.getApto().getNombre() != null) nombres.add(pa.getApto().getNombre());
        }
        return String.join(", ", nombres);
    }
}
