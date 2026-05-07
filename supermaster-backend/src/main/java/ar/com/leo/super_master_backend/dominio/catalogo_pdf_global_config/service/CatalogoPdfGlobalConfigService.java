package ar.com.leo.super_master_backend.dominio.catalogo_pdf_global_config.service;

import ar.com.leo.super_master_backend.dominio.catalogo_pdf_global_config.dto.CatalogoPdfGlobalConfigDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CatalogoPdfGlobalConfigService {

    @Value("${app.imagenes-dir:C:/ProgramData/SuperMaster/imagenes/}")
    private String imagenesDir;

    public CatalogoPdfGlobalConfigDTO obtener() {
        return new CatalogoPdfGlobalConfigDTO(imagenesDir);
    }
}
