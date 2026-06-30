package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.service.ImagenIaConfigService;
import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orquesta la carátula: cruda → OpenAI → imagen → disco. */
@Service
@RequiredArgsConstructor
public class CaratulaService {

    private final ImagenService imagenService;
    private final OpenAiImagenService openAiImagenService;
    private final ImagenIaConfigService configService;

    /** Genera (sin guardar) a partir de la cruda automática del SKU. */
    public GeneracionCaratula generar(String sku) {
        return generar(sku, null);
    }

    /**
     * Genera (sin guardar) a partir de una cruda elegida del SKU. Si {@code crudaNombre} es null,
     * resuelve la cruda automáticamente. Si no es null, valida que pertenezca al SKU.
     */
    public GeneracionCaratula generar(String sku, String crudaNombre) {
        String nombre;
        if (crudaNombre == null) {
            nombre = imagenService.resolverCrudaPorSku(sku);
            if (nombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        } else {
            if (!imagenService.resolverCrudasPorSku(sku).contains(crudaNombre))
                throw new IllegalArgumentException("La imagen elegida no pertenece al SKU " + sku);
            nombre = crudaNombre;
        }
        byte[] cruda = imagenService.leerCrudaBytes(nombre);
        byte[] generada = openAiImagenService.generarCaratula(cruda, nombre);
        return new GeneracionCaratula(cruda, nombre, generada);
    }

    /** Formato configurado (output_format), p.ej. "jpeg"/"png"/"webp". */
    public String formato() {
        return configService.cargar().getOutputFormat();
    }

    public void guardar(String sku, byte[] datos) {
        imagenService.guardarCaratula(sku, datos, extDe(formato()));
        imagenService.eliminarCrudasPorSku(sku);
    }

    /** jpeg → jpg; el resto coincide con la extensión. */
    private static String extDe(String formato) {
        return "jpeg".equals(formato) ? "jpg" : formato;
    }
}
