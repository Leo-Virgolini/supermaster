package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CatalogoPdfConfigDTO(
        Integer id,
        String nombre,
        Integer canalId,
        String canalNombre,
        Integer catalogoId,
        String catalogoNombre,
        Integer cuotas,
        List<String> ordenarPor,
        Integer clasifGralId,
        String clasificacion,
        Integer tipoId,
        String tipoNombre,
        Integer marcaId,
        String marcaNombre,
        String tag,
        Boolean caratula,
        String titulo,
        String estetica,
        String tipoDocumento,
        Integer productosPorPagina,
        String ubicacionSalida,
        Boolean activo,
        Boolean soloActivos,
        LocalDateTime fechaModificacion
) {}
