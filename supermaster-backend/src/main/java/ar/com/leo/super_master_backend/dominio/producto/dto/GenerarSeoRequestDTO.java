package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.dto.SeoContexto;

/** Pedido para generar SEO de Nube con IA: canal (prompt) + contexto del producto. */
public record GenerarSeoRequestDTO(SeoCanal canal, SeoContexto contexto) {}
