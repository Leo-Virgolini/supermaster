package ar.com.leo.super_master_backend.apis.openai.dto;

import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService.EstadoCarpeta;

import java.util.List;

/** Crudas disponibles de un SKU + diagnóstico de las carpetas, para el selector de carátula. */
public record CrudasDisponiblesDTO(EstadoCarpeta crudaDir, EstadoCarpeta destinoDir, List<String> imagenes) {}
