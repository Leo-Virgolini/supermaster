package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.service;

import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigCreateDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigDTO;
import ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.dto.CatalogoPdfConfigUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogoPdfConfigService {
    Page<CatalogoPdfConfigDTO> listar(String search, Pageable pageable);
    CatalogoPdfConfigDTO obtener(Integer id);
    CatalogoPdfConfigDTO crear(CatalogoPdfConfigCreateDTO dto);
    CatalogoPdfConfigDTO actualizar(Integer id, CatalogoPdfConfigUpdateDTO dto);
    void eliminar(Integer id);
}
