package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO.CanalAplicado;
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
    public EstadoAplicarDTO aplicar(Integer productoId, EstadoPublicacionUpdateDTO cambios) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        CanalAplicado ml = cambios.ml() != null ? aplicarMl(p, cambios.ml()) : null;
        CanalAplicado hogar = cambios.hogar() != null ? aplicarNube(p.getSku(), TiendaNubeService.STORE_HOGAR, cambios.hogar()) : null;
        CanalAplicado gastro = cambios.gastro() != null ? aplicarNube(p.getSku(), TiendaNubeService.STORE_GASTRO, cambios.gastro()) : null;
        return new EstadoAplicarDTO(ml, hogar, gastro);
    }

    private CanalAplicado aplicarMl(Producto p, String estado) {
        if (!"active".equals(estado) && !"paused".equals(estado))
            return new CanalAplicado(false, "Estado inválido");
        String mla = p.getMla() != null ? p.getMla().getMla() : null;
        if (mla == null || mla.isBlank())
            return new CanalAplicado(false, "Sin publicación en Mercado Libre");
        boolean ok = mercadoLibreService.updateItemStatus(mla, estado);
        if (!ok) return new CanalAplicado(false, "Mercado Libre rechazó el cambio");
        return new CanalAplicado(true, "active".equals(estado) ? "Activada" : "Pausada");
    }

    private CanalAplicado aplicarNube(String sku, String store, boolean visible) {
        JsonNode product;
        try { product = tiendaNubeService.buscarProductoPorSku(sku, store); }
        catch (Exception e) { return new CanalAplicado(false, "Error consultando Nube"); }
        if (product == null) return new CanalAplicado(false, "No publicado en " + store);
        long productId = product.path("id").asLong();
        boolean ok = tiendaNubeService.actualizarPublished(store, productId, visible);
        if (!ok) return new CanalAplicado(false, "Nube rechazó el cambio");
        return new CanalAplicado(true, visible ? "Visible" : "Oculta");
    }
}
