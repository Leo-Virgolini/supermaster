package ar.com.leo.super_master_backend.apis.openai.dto;

import java.util.List;

/** Datos del producto que se le pasan a la IA para generar el SEO. */
public record SeoContexto(
        String tituloNube,
        String marca,
        String material,
        List<String> aptos,
        List<String> dimensiones
) {}
