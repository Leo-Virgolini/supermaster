package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeRequestDTO(List<String> skus, List<DestinoNube> tiendas) {
    public record DestinoNube(String tienda, Integer cuotas) {}
}
