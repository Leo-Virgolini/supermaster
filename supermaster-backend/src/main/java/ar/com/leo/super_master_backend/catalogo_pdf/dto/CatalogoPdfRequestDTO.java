package ar.com.leo.super_master_backend.catalogo_pdf.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import jakarta.validation.constraints.NotNull;

public record CatalogoPdfRequestDTO(
        @NotNull(message = "catalogoId es requerido")
        Integer catalogoId,
        @NotNull(message = "canalId es requerido")
        Integer canalId,
        @NotNull(message = "cuotas es requerido")
        Integer cuotas,
        Integer clasifGralId,
        Integer clasifGastroId,
        Integer tipoId,
        Integer marcaId,
        Tag tag,
        String ordenarPor,
        String titulo,
        String subtitulo,
        Boolean incluirImagenes,
        Boolean caratula,
        String estetica,
        String tipoDocumento,
        Integer productosPorPagina,
        String tipoHoja,
        Boolean mostrarCodigo,
        Float fontSizeCodigo,
        String colorCodigo,
        Boolean mostrarNombre,
        Float fontSizeNombre,
        String colorNombre,
        Boolean mostrarPrecio,
        Float fontSizePrecio,
        String colorPrecio,
        Boolean mostrarUxb,
        Float fontSizeUxb,
        String colorUxb
) {
}
