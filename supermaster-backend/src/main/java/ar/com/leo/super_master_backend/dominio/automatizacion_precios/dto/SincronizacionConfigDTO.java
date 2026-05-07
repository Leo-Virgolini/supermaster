package ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto;

public record SincronizacionConfigDTO(
        Integer sellerCampaignPct,
        Integer dealPct,
        Integer smartPct,
        String canalMl,
        Integer cuotasMl,
        String listaPreciosMl,
        Boolean sinIvaMl,
        String canalGastro,
        Integer cuotasGastro,
        String listaPreciosGastro,
        Boolean sinIvaGastro,
        String canalHogar,
        Integer cuotasHogar,
        String listaPreciosHogar,
        Boolean sinIvaHogar
) {}
