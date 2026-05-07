package ar.com.leo.super_master_backend.apis.ml.dto;

import java.math.BigDecimal;

public record CostoVentaResponseDTO(
        String mla,
        String status,
        BigDecimal precioConsultado,
        // Totales
        BigDecimal comisionVentaTotal,    // sale_fee_amount
        // Detalles desglosados de sale_fee_details
        BigDecimal costoFijo,             // sale_fee_details.fixed_fee
        BigDecimal cargoFinanciacion,     // sale_fee_details.financing_add_on_fee
        BigDecimal porcentajeMeli,        // sale_fee_details.meli_percentage_fee
        BigDecimal porcentajeTotal,       // sale_fee_details.percentage_fee
        // Metadata
        String listingTypeId,
        String listingTypeName,
        String mensaje
) {}
