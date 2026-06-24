package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;

/** Construye los prompts (system/user) para la generación de SEO. Lógica pura, sin red. */
public final class OpenAiSeoPrompts {

    private OpenAiSeoPrompts() {}

    static final String SYSTEM_BASE = """
            Eres un especialista en SEO para ecommerce.

            Genera exclusivamente un JSON válido con las siguientes propiedades:

            {
              "seo_title": "",
              "seo_description": "",
              "tags": ""
            }

            Reglas:
            - seo_title: máximo 70 caracteres.
            - seo_description: máximo 320 caracteres.
            - tags: entre 4 y 6 tags separados por comas; sin códigos, SKU ni el texto entre paréntesis.
            - No inventar características que no estén presentes en la información.
            - Optimizar para búsquedas en español.
            - No incluir explicaciones ni texto fuera del JSON.
            - No incluir códigos internos, SKU ni referencias (por ejemplo textos entre paréntesis como 712B) en ningún campo.
            - No agregar al seo_title sufijos ni etiquetas de rubro o categoría (por ejemplo nada de "- Gastronómico" al final).""";

    /** Regla extra solo para el canal gastronómico. */
    static final String REGLA_GASTRO =
            "\n- Orientá el vocabulario y las palabras clave al rubro gastronómico y profesional"
            + " (uso en cocinas de restaurantes, cafeterías, pastelerías, panaderías, pizzerías y bares),"
            + " pero NO enumeres literalmente esos comercios en la descripción ni escribas frases del tipo"
            + " \"apto para restaurantes, cafeterías...\".";

    public static String systemPrompt(SeoCanal canal) {
        return canal == SeoCanal.GASTRO ? SYSTEM_BASE + REGLA_GASTRO : SYSTEM_BASE;
    }

    /** Arma el mensaje de usuario con la info del producto en texto plano. */
    public static String userMessage(SeoContexto c) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(c.tituloNube())) sb.append("Título: ").append(c.tituloNube().trim()).append("\n");
        if (notBlank(c.tituloDux())) sb.append("Nombre interno: ").append(c.tituloDux().trim()).append("\n");
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
}
