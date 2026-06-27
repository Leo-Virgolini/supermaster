package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class EstadoPublicacionService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final TiendaNubeService tiendaNubeService;

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
    }

    @Transactional(readOnly = true)
    public EstadoPublicacionDTO leer(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        return new EstadoPublicacionDTO(
                leerMl(p),
                leerNube(p.getSku(), TiendaNubeService.STORE_HOGAR),
                leerNube(p.getSku(), TiendaNubeService.STORE_GASTRO));
    }

    private EstadoCanalDTO leerMl(Producto p) {
        String mlaCode = p.getMla() != null ? p.getMla().getMla() : null;
        if (mlaCode == null || mlaCode.isBlank()) return EstadoCanalDTO.noPublicado();
        JsonNode item = mercadoLibreService.leerItemRaw(mlaCode);
        if (item == null) return EstadoCanalDTO.ofError();
        return MlEstadoParser.parse(item);
    }

    private EstadoCanalDTO leerNube(String sku, String store) {
        JsonNode product;
        try {
            product = tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return EstadoCanalDTO.ofError();
        }
        if (product == null) return EstadoCanalDTO.noPublicado();
        return NubeEstadoParser.parse(product);
    }

    @Transactional(readOnly = true)
    public void aplicar(Integer productoId, EstadoPublicacionUpdateDTO cambios) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        if (cambios.ml() != null
                && ("active".equals(cambios.ml()) || "paused".equals(cambios.ml()))
                && p.getMla() != null
                && p.getMla().getMla() != null) {
            mercadoLibreService.updateItemStatus(p.getMla().getMla(), cambios.ml());
        }
        if (cambios.hogar() != null) aplicarNube(p.getSku(), TiendaNubeService.STORE_HOGAR, cambios.hogar());
        if (cambios.gastro() != null) aplicarNube(p.getSku(), TiendaNubeService.STORE_GASTRO, cambios.gastro());
    }

    private void aplicarNube(String sku, String store, boolean visible) {
        JsonNode product = tiendaNubeService.buscarProductoPorSku(sku, store);
        if (product == null) return; // no publicado en esa tienda: nada que cambiar
        long productId = product.path("id").asLong();
        tiendaNubeService.actualizarPublished(store, productId, visible);
    }
}
