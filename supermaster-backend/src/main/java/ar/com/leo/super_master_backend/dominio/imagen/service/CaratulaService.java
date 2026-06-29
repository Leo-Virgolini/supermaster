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

    /** Genera (sin guardar) la carátula a partir de la cruda del SKU; devuelve cruda + generada. */
    public GeneracionCaratula generar(String sku) {
        String crudaNombre = imagenService.resolverCrudaPorSku(sku);
        if (crudaNombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        byte[] cruda = imagenService.leerCrudaBytes(crudaNombre);
        byte[] generada = openAiImagenService.generarCaratula(cruda, crudaNombre);
        return new GeneracionCaratula(cruda, crudaNombre, generada);
    }

    /** Formato configurado (output_format), p.ej. "jpeg"/"png"/"webp". */
    public String formato() {
        return configService.cargar().getOutputFormat();
    }

    public void guardar(String sku, byte[] datos) {
        imagenService.guardarCaratula(sku, datos, extDe(formato()));
    }

    /** jpeg → jpg; el resto coincide con la extensión. */
    private static String extDe(String formato) {
        return "jpeg".equals(formato) ? "jpg" : formato;
    }
}
