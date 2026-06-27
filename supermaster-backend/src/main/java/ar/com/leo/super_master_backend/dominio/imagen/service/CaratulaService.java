package ar.com.leo.super_master_backend.dominio.imagen.service;

import ar.com.leo.super_master_backend.apis.openai.service.OpenAiImagenService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Orquesta la carátula: cruda → OpenAI → JPG → disco. */
@Service
@RequiredArgsConstructor
public class CaratulaService {

    private final ImagenService imagenService;
    private final OpenAiImagenService openAiImagenService;

    /** Genera (sin guardar) la carátula JPG a partir de la cruda del SKU. */
    public byte[] generar(String sku) {
        String crudaNombre = imagenService.resolverCrudaPorSku(sku);
        if (crudaNombre == null) throw new NotFoundException("No hay imagen cruda para el SKU " + sku);
        byte[] cruda = imagenService.leerCrudaBytes(crudaNombre);
        return openAiImagenService.generarCaratula(cruda, crudaNombre);
    }

    public void guardar(String sku, byte[] jpg) {
        imagenService.guardarCaratula(sku, jpg);
    }
}
