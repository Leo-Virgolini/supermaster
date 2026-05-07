package ar.com.leo.super_master_backend.catalogo_pdf.controller;

import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfRequestDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.service.CatalogoPdfService;
import ar.com.leo.super_master_backend.dominio.common.response.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import ar.com.leo.super_master_backend.config.Permisos;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalogos-pdf")
public class CatalogoPdfController {

    private final CatalogoPdfService catalogoPdfService;

    @GetMapping("/exportar")
    @PreAuthorize(Permisos.CATALOGOS_PDF_VER)
    public ResponseEntity<?> exportarCatalogoPdf(
            @RequestParam("catalogoId") Integer catalogoId,
            @RequestParam("canalId") Integer canalId,
            @RequestParam("cuotas") Integer cuotas,
            @RequestParam(value = "clasifGralId", required = false) Integer clasifGralId,
            @RequestParam(value = "clasifGastroId", required = false) Integer clasifGastroId,
            @RequestParam(value = "tipoId", required = false) Integer tipoId,
            @RequestParam(value = "marcaId", required = false) Integer marcaId,
            @RequestParam(value = "tag", required = false) ar.com.leo.super_master_backend.dominio.producto.entity.Tag tag,
            @RequestParam(value = "ordenarPor", required = false) String ordenarPor,
            @RequestParam(value = "titulo", required = false) String titulo,
            @RequestParam(value = "subtitulo", required = false) String subtitulo,
            @RequestParam(value = "incluirImagenes", required = false) Boolean incluirImagenes,
            @RequestParam(value = "caratula", required = false) Boolean caratula,
            @RequestParam(value = "estetica", required = false) String estetica,
            @RequestParam(value = "tipoDocumento", required = false) String tipoDocumento,
            @RequestParam(value = "productosPorPagina", required = false) Integer productosPorPagina,
            @RequestParam(value = "tipoHoja", required = false) String tipoHoja,
            @RequestParam(value = "mostrarCodigo", required = false) Boolean mostrarCodigo,
            @RequestParam(value = "fontSizeCodigo", required = false) Float fontSizeCodigo,
            @RequestParam(value = "colorCodigo", required = false) String colorCodigo,
            @RequestParam(value = "mostrarNombre", required = false) Boolean mostrarNombre,
            @RequestParam(value = "fontSizeNombre", required = false) Float fontSizeNombre,
            @RequestParam(value = "colorNombre", required = false) String colorNombre,
            @RequestParam(value = "mostrarPrecio", required = false) Boolean mostrarPrecio,
            @RequestParam(value = "fontSizePrecio", required = false) Float fontSizePrecio,
            @RequestParam(value = "colorPrecio", required = false) String colorPrecio,
            @RequestParam(value = "mostrarUxb", required = false) Boolean mostrarUxb,
            @RequestParam(value = "fontSizeUxb", required = false) Float fontSizeUxb,
            @RequestParam(value = "colorUxb", required = false) String colorUxb
    ) {
        CatalogoPdfRequestDTO request = new CatalogoPdfRequestDTO(
                catalogoId,
                canalId,
                cuotas,
                clasifGralId,
                clasifGastroId,
                tipoId,
                marcaId,
                tag,
                ordenarPor,
                titulo,
                subtitulo,
                incluirImagenes,
                caratula,
                estetica,
                tipoDocumento,
                productosPorPagina,
                tipoHoja,
                mostrarCodigo,
                fontSizeCodigo,
                colorCodigo,
                mostrarNombre,
                fontSizeNombre,
                colorNombre,
                mostrarPrecio,
                fontSizePrecio,
                colorPrecio,
                mostrarUxb,
                fontSizeUxb,
                colorUxb
        );
        return generarRespuestaPdf(request, "/api/catalogos-pdf/exportar");
    }

    @PostMapping("/generar-automatico-todos")
    public ResponseEntity<GenerarTodosResultDTO> generarTodosLosAutomaticos() {
        GenerarTodosResultDTO result = catalogoPdfService.generarTodosLosAutomaticos();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/generar-automatico/{id}")
    public ResponseEntity<?> generarCatalogoPdfAutomaticoPorId(@PathVariable Integer id) {
        String path = "/api/catalogos-pdf/generar-automatico/" + id;
        try {
            CatalogoPdfResultDTO result = catalogoPdfService.exportarCatalogoPdfDesdeConfig(id);

            // Si hay ubicación de salida configurada, el archivo ya fue guardado en disco
            if (result.rutaGuardada() != null) {
                java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("mensaje", "PDF generado y guardado correctamente");
                response.put("ruta", result.rutaGuardada());
                response.put("productosExportados", result.productosExportados());
                response.put("productosSinImagen", result.productosSinImagen() != null ? result.productosSinImagen() : java.util.List.of());
                return ResponseEntity.ok(response);
            }

            // Sin ubicación de salida: descarga directa vía browser
            String filename = String.format("CATALOGO_%s_%s.pdf",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);
            headers.add("X-Productos-Count", String.valueOf(result.productosExportados()));
            if (result.productosSinImagen() != null && !result.productosSinImagen().isEmpty()) {
                headers.add("X-Productos-Sin-Imagen", String.join(",", result.productosSinImagen()));
                headers.add("X-Productos-Sin-Imagen-Count", String.valueOf(result.productosSinImagen().size()));
            }
            headers.add("Access-Control-Expose-Headers", "X-Productos-Count,X-Productos-Sin-Imagen,X-Productos-Sin-Imagen-Count");
            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al generar catálogo PDF automático: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage(), path));
        } catch (IOException e) {
            log.error("Error de I/O al generar catálogo PDF automático: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo PDF: " + e.getMessage(), path));
        } catch (Exception e) {
            log.error("Error inesperado al generar catálogo PDF automático: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), path));
        }
    }

    @PostMapping(value = "/generar-automatico", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(Permisos.CATALOGOS_PDF_VER)
    public ResponseEntity<?> generarCatalogoPdfAutomatico(@Valid @RequestBody CatalogoPdfRequestDTO request) {
        return generarRespuestaPdf(request, "/api/catalogos-pdf/generar-automatico");
    }

    private ResponseEntity<?> generarRespuestaPdf(CatalogoPdfRequestDTO request, String path) {
        try {
            CatalogoPdfResultDTO result = catalogoPdfService.exportarCatalogoPdf(request);
            String filename = String.format("CATALOGO_%s_%s.pdf",
                    result.nombreArchivo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(result.archivo().length);
            headers.add("X-Productos-Count", String.valueOf(result.productosExportados()));
            if (result.productosSinImagen() != null && !result.productosSinImagen().isEmpty()) {
                headers.add("X-Productos-Sin-Imagen", String.join(",", result.productosSinImagen()));
                headers.add("X-Productos-Sin-Imagen-Count", String.valueOf(result.productosSinImagen().size()));
            }
            headers.add("Access-Control-Expose-Headers", "X-Productos-Count,X-Productos-Sin-Imagen,X-Productos-Sin-Imagen-Count");

            return new ResponseEntity<>(result.archivo(), headers, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Error de validación al generar catálogo PDF: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ErrorResponse.of(e.getMessage(), path));
        } catch (IOException e) {
            log.error("Error de I/O al generar catálogo PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error al generar el archivo PDF: " + e.getMessage(), path));
        } catch (Exception e) {
            log.error("Error inesperado al generar catálogo PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of("Error interno del servidor: " + e.getMessage(), path));
        }
    }
}
