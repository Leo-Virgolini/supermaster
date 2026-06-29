package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.DatosCanalDTO;
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
    private final DuxService duxService;

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService,
                                    DuxService duxService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
        this.duxService = duxService;
    }

    @Transactional(readOnly = true)
    public EstadoPublicacionDTO leer(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));

        // --- ML: una sola GET del ítem, reutilizada para estado y datos ---
        String mlaCode = p.getMla() != null ? p.getMla().getMla() : null;
        JsonNode mlItem = (mlaCode != null && !mlaCode.isBlank()) ? mercadoLibreService.leerItemRaw(mlaCode) : null;
        EstadoCanalDTO ml = estadoMl(mlaCode, mlItem);
        String descMl = (mlaCode != null && !mlaCode.isBlank()) ? mercadoLibreService.leerDescripcionMl(mlaCode) : null;

        // --- Nube: una sola GET por tienda, reutilizada para estado y descripción ---
        JsonNode hogarProd;
        EstadoCanalDTO hogar;
        try {
            hogarProd = tiendaNubeService.buscarProductoPorSku(p.getSku(), TiendaNubeService.STORE_HOGAR);
            hogar = estadoNube(hogarProd);
        } catch (Exception e) {
            hogarProd = null;
            hogar = EstadoCanalDTO.ofError();
        }
        JsonNode gastroProd;
        EstadoCanalDTO gastro;
        try {
            gastroProd = tiendaNubeService.buscarProductoPorSku(p.getSku(), TiendaNubeService.STORE_GASTRO);
            gastro = estadoNube(gastroProd);
        } catch (Exception e) {
            gastroProd = null;
            gastro = EstadoCanalDTO.ofError();
        }

        // --- Dux ---
        EstadoCanalDTO dux = estadoDux(p.getSku());

        DatosCanalDTO datos = new DatosCanalDTO(
                MlDatosParser.categoryId(mlItem),
                null, // nombre de categoría no viene en /items/{id}
                MlDatosParser.atributos(mlItem),
                descMl,
                descripcionNube(hogarProd),
                descripcionNube(gastroProd));

        return new EstadoPublicacionDTO(ml, hogar, gastro, dux, datos);
    }

    private EstadoCanalDTO estadoMl(String mlaCode, JsonNode item) {
        if (mlaCode == null || mlaCode.isBlank()) return EstadoCanalDTO.noPublicado();
        if (item == null) return EstadoCanalDTO.ofError();
        return MlEstadoParser.parse(item);
    }

    private EstadoCanalDTO estadoNube(JsonNode product) {
        if (product == null) return EstadoCanalDTO.noPublicado();
        return NubeEstadoParser.parse(product);
    }

    private String descripcionNube(JsonNode product) {
        if (product == null) return null;
        return product.path("description").path("es").asString(null);
    }

    private EstadoCanalDTO estadoDux(String sku) {
        try {
            Item item = duxService.obtenerProductoPorCodigo(sku);
            return DuxEstadoParser.parse(item);
        } catch (Exception e) {
            return EstadoCanalDTO.ofError();
        }
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
