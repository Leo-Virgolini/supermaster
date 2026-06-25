package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;

/** Construye el mensaje de usuario (datos del producto) para la generación de SEO. Lógica pura, sin red. */
public final class OpenAiSeoPrompts {

    private OpenAiSeoPrompts() {}

    /** Arma el mensaje de usuario con la info del producto en texto plano. */
    public static String userMessage(SeoContexto c) {
        StringBuilder sb = new StringBuilder();
        // El "nombre interno" (título Dux) NO se manda: trae abreviaturas y códigos
        // internos que ensucian el SEO. El título Nube + las características alcanzan.
        String titulo = sinParentesis(c.tituloNube());
        if (notBlank(titulo)) sb.append("Título: ").append(titulo).append("\n");
        if (notBlank(c.marca())) sb.append("Marca: ").append(c.marca().trim()).append("\n");
        if (notBlank(c.material())) sb.append("Material: ").append(c.material().trim()).append("\n");
        if (c.dimensiones() != null) {
            for (String d : c.dimensiones()) {
                if (notBlank(d)) sb.append("- ").append(d.trim()).append("\n");
            }
        }
        if (c.aptos() != null) {
            var aptos = c.aptos().stream().filter(OpenAiSeoPrompts::notBlank).map(String::trim).toList();
            if (!aptos.isEmpty()) sb.append("Apto para: ").append(String.join(", ", aptos)).append("\n");
        }
        return sb.toString().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Quita el contenido entre paréntesis (códigos internos como "(712B)") y colapsa espacios. */
    private static String sinParentesis(String s) {
        if (s == null) return "";
        return s.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", " ").trim();
    }
}
