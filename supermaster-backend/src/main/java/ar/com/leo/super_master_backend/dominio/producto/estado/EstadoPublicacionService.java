package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
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
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class EstadoPublicacionService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final TiendaNubeService tiendaNubeService;
    private final DuxService duxService;

    // Pool para leer los canales en paralelo. Hilos daemon (no bloquean el shutdown de la JVM);
    // cached porque las lecturas son I/O a APIs externas, ráfagas cortas al abrir el modal.
    private final ExecutorService estadoPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "estado-publicacion");
        t.setDaemon(true);
        return t;
    });

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService,
                                    DuxService duxService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
        this.duxService = duxService;
    }

    @PreDestroy
    void cerrarPool() {
        estadoPool.shutdown();
    }

    /** Datos del panel + editables de ML resueltos en un solo paso (para leer en un hilo aparte). */
    private record MlPanel(EstadoCanalDTO estado, String categoryId, String categoryNombre,
                           List<MlAtributoDTO> atributos, String descripcion, String mlaResuelto,
                           MlDatosParser.PaqueteMl paquete) {}

    /** Estado + datos editables de una tienda Nube. */
    private record NubePanel(EstadoCanalDTO estado, String descripcion, SeoCanalDTO seo,
                             String peso, String profundidad, String ancho, String alto, String titulo) {}

    @Transactional(readOnly = true)
    public EstadoPublicacionDTO leer(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String sku = p.getSku();

        // Los 4 canales se leen EN PARALELO: son solo llamadas I/O a APIs externas (ML/Nube/Dux) que
        // usan el SKU ya extraído, no la sesión JPA. Cada tarea captura su propio error y nunca lanza,
        // así un canal caído no rompe a los demás. El tiempo total ≈ el canal más lento (antes era la suma).
        CompletableFuture<MlPanel> fMl = CompletableFuture.supplyAsync(() -> leerMlPanel(sku), estadoPool);
        CompletableFuture<NubePanel> fHogar = CompletableFuture.supplyAsync(
                () -> leerNubePanel(sku, TiendaNubeService.STORE_HOGAR), estadoPool);
        CompletableFuture<NubePanel> fGastro = CompletableFuture.supplyAsync(
                () -> leerNubePanel(sku, TiendaNubeService.STORE_GASTRO), estadoPool);
        CompletableFuture<EstadoCanalDTO> fDux = CompletableFuture.supplyAsync(() -> estadoDux(sku), estadoPool);

        MlPanel ml = fMl.join();
        NubePanel hogar = fHogar.join();
        NubePanel gastro = fGastro.join();
        EstadoCanalDTO dux = fDux.join();

        DatosCanalDTO datos = new DatosCanalDTO(
                ml.categoryId(),
                ml.categoryNombre(),
                ml.atributos(),
                ml.descripcion(),
                hogar.descripcion(),
                gastro.descripcion(),
                hogar.seo(),
                gastro.seo(),
                ml.mlaResuelto(),
                hogar.peso() != null ? hogar.peso() : gastro.peso(),
                hogar.profundidad() != null ? hogar.profundidad() : gastro.profundidad(),
                hogar.ancho() != null ? hogar.ancho() : gastro.ancho(),
                hogar.alto() != null ? hogar.alto() : gastro.alto(),
                hogar.titulo() != null ? hogar.titulo() : gastro.titulo(),
                ml.paquete().altoCm(),
                ml.paquete().anchoCm(),
                ml.paquete().largoCm(),
                ml.paquete().pesoKg());

        return new EstadoPublicacionDTO(ml.estado(), hogar.estado(), gastro.estado(), dux, datos);
    }

    /**
     * Lee ML: el MLA se resuelve por SKU contra la API (no desde la BD), para reflejar la publicación
     * REAL y vigente. Un id_mla guardado puede estar stale/mal vinculado. Nunca lanza (todo en try/catch
     * → ofError); early-return si item==null para no llamar descripción/categoría sobre un ítem inválido.
     */
    private MlPanel leerMlPanel(String sku) {
        try {
            String mlaCode = resolverMlaPorSku(sku);
            if (mlaCode == null || mlaCode.isBlank()) {
                return new MlPanel(EstadoCanalDTO.noPublicado(), null, null, List.of(), null, null,
                        new MlDatosParser.PaqueteMl(null, null, null, null));
            }
            JsonNode item = mercadoLibreService.leerItemRaw(mlaCode);
            if (item == null) {
                return new MlPanel(EstadoCanalDTO.ofError(), null, null, List.of(), null, null,
                        new MlDatosParser.PaqueteMl(null, null, null, null));
            }
            String descMl = mercadoLibreService.leerDescripcionMl(mlaCode);
            String catId = MlDatosParser.categoryId(item);
            String catNombre = (catId != null && !catId.isBlank()) ? mercadoLibreService.obtenerCategoriaPath(catId) : null;
            MlDatosParser.PaqueteMl paquete = MlDatosParser.paquete(item);
            return new MlPanel(MlEstadoParser.parse(item), catId, catNombre,
                    MlDatosParser.atributos(item), descMl, mlaCode, paquete);
        } catch (Exception e) {
            return new MlPanel(EstadoCanalDTO.ofError(), null, null, List.of(), null, null,
                    new MlDatosParser.PaqueteMl(null, null, null, null));
        }
    }

    /** Resuelve el código MLA real por SKU contra la API de ML, en cualquier estado vigente (active/paused).
     *  Null si no hay publicación tradicional (las cerradas/borradas no las devuelve la búsqueda). */
    private String resolverMlaPorSku(String sku) {
        var hallado = mercadoLibreService.buscarMlaPorSkuCualquierEstado(sku);
        return hallado != null ? hallado.mla() : null;
    }

    /** Lee una tienda Nube (una sola GET reutilizada para estado, descripción y SEO). Nunca lanza. */
    private NubePanel leerNubePanel(String sku, String store) {
        JsonNode product;
        try {
            product = tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return new NubePanel(EstadoCanalDTO.ofError(), null, null, null, null, null, null, null);
        }
        JsonNode variant = (product != null) ? product.path("variants").path(0) : null;
        String peso = variant != null ? variant.path("weight").asString(null) : null;
        String prof = variant != null ? variant.path("depth").asString(null) : null;
        String ancho = variant != null ? variant.path("width").asString(null) : null;
        String alto = variant != null ? variant.path("height").asString(null) : null;
        String titulo = (product != null) ? product.path("name").path("es").asString(null) : null;
        return new NubePanel(estadoNube(product), descripcionNube(product), NubeSeoParser.parse(product),
                peso, prof, ancho, alto, titulo);
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
        String mla;
        try { mla = resolverMlaPorSku(p.getSku()); }
        catch (Exception e) { return new CanalAplicado(false, "Error consultando Mercado Libre"); }
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
