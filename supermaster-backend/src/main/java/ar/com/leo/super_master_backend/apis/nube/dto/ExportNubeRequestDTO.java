package ar.com.leo.super_master_backend.apis.nube.dto;

import java.util.List;

public record ExportNubeRequestDTO(List<String> skus, List<DestinoNube> tiendas) {
    /**
     * Datos de exportación por tienda Nube. El paquete de envío (peso/dimensiones) y el título
     * son ahora por canal: cada store trae los suyos al editar y se le mandan los suyos al exportar.
     * {@code titulo} es el override por canal; si viene vacío, el service cae al título base del producto.
     */
    public record DestinoNube(String tienda, Integer cuotas,
                              ar.com.leo.super_master_backend.apis.openai.dto.SeoGeneradoDTO seo,
                              String descripcion, String titulo,
                              String peso, String profundidad, String ancho, String alto) {}
}
