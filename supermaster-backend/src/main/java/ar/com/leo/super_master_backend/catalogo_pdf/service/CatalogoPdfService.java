package ar.com.leo.super_master_backend.catalogo_pdf.service;

import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfRequestDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.CatalogoPdfResultDTO;
import ar.com.leo.super_master_backend.catalogo_pdf.dto.GenerarTodosResultDTO;

import java.io.IOException;

public interface CatalogoPdfService {
    CatalogoPdfResultDTO exportarCatalogoPdfDesdeConfig(Integer configId) throws IOException;
    CatalogoPdfResultDTO exportarCatalogoPdf(CatalogoPdfRequestDTO request) throws IOException;
    GenerarTodosResultDTO generarTodosLosAutomaticos();
}

