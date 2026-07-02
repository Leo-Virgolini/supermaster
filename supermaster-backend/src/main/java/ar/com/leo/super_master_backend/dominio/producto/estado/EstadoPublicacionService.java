package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDefDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.ml.service.MlCategoriaAtributoService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.DuxCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.FamiliaMlDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.FamiliaVarianteDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoAplicarDTO.CanalAplicado;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.MlCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.NubeCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoPublicacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.SeoCanalDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EstadoPublicacionService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final TiendaNubeService tiendaNubeService;
    private final DuxService duxService;
    private final MlaRepository mlaRepository;
    private final MlCategoriaAtributoService mlCategoriaAtributoService;

    public EstadoPublicacionService(ProductoRepository productoRepository,
                                    MercadoLibreService mercadoLibreService,
                                    TiendaNubeService tiendaNubeService,
                                    DuxService duxService,
                                    MlaRepository mlaRepository,
                                    MlCategoriaAtributoService mlCategoriaAtributoService) {
        this.productoRepository = productoRepository;
        this.mercadoLibreService = mercadoLibreService;
        this.tiendaNubeService = tiendaNubeService;
        this.duxService = duxService;
        this.mlaRepository = mlaRepository;
        this.mlCategoriaAtributoService = mlCategoriaAtributoService;
    }

    /**
     * Lee ML: el MLA se resuelve por SKU contra la API (no desde la BD), para reflejar la publicación
     * REAL y vigente. Un id_mla guardado puede estar stale/mal vinculado. Nunca lanza (todo en try/catch
     * → ofError); early-return si item==null para no llamar descripción/categoría sobre un ítem inválido.
     */
    @Transactional(readOnly = true)
    public MlCanalDTO leerMl(Integer id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String sku = p.getSku();
        try {
            String mlaCode = resolverMlaPorSku(sku);
            if (mlaCode == null || mlaCode.isBlank()) {
                return new MlCanalDTO(EstadoCanalDTO.noPublicado(), null, null, List.of(), null, null,
                        null, null, null, null, null, null);
            }
            JsonNode item = mercadoLibreService.leerItemRaw(mlaCode);
            if (item == null) {
                return new MlCanalDTO(EstadoCanalDTO.ofError(), null, null, List.of(), null, null,
                        null, null, null, null, null, null);
            }
            String descMl = mercadoLibreService.leerDescripcionMl(mlaCode);
            String catId = MlDatosParser.categoryId(item);
            String catNombre = (catId != null && !catId.isBlank()) ? mercadoLibreService.obtenerCategoriaPath(catId) : null;
            MlDatosParser.PaqueteMl paquete = MlDatosParser.paquete(item);
            List<MlAtributoDTO> atributos = MlDatosParser.atributos(item);
            // Modelo nuevo (User Products): el `title` lo autogenera ML agregando el valor de la variante
            // (ej. "...Plateado"), pero lo editable/genérico (y que respeta los 60 chars) es el `family_name`.
            // Preferimos family_name; para publicaciones viejas (sin familia) caemos al title.
            String familyName = item.path("family_name").asString(null);
            String titulo = (familyName != null && !familyName.isBlank()) ? familyName : item.path("title").asString(null);
            String ean = MlDatosParser.codigoUniversal(item);
            return new MlCanalDTO(MlEstadoParser.parse(item), catId, catNombre,
                    atributos, descMl, mlaCode,
                    paquete.altoCm(), paquete.anchoCm(), paquete.largoCm(), paquete.pesoKg(), titulo, ean);
        } catch (Exception e) {
            return new MlCanalDTO(EstadoCanalDTO.ofError(), null, null, List.of(), null, null,
                    null, null, null, null, null, null);
        }
    }

    /**
     * Familia de variantes del producto (modelo nuevo). 2b-1: se arma desde la BD por family_id
     * (los hermanos son productos cuyo MLA comparte family_id). Si el producto no es de familia,
     * devuelve {@link FamiliaMlDTO#ninguna()}.
     */
    @Transactional(readOnly = true)
    public FamiliaMlDTO leerFamilia(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        Mla mla = p.getMla();
        String mlaCode = (mla != null) ? mla.getMla() : null;

        // Lee el ítem actual de ML (best-effort) para detectar el modelo y enriquecer.
        JsonNode item = null;
        try { if (mlaCode != null && !mlaCode.isBlank()) item = mercadoLibreService.leerItemRaw(mlaCode); }
        catch (Exception ignored) { /* sin ML seguimos con la BD */ }

        String familyName = (item != null) ? item.path("family_name").asString(null) : null;
        JsonNode variations = (item != null) ? item.path("variations") : null;
        boolean hayVariations = variations != null && variations.isArray() && !variations.isEmpty();

        // LEGACY: sin family_name pero con variations[] anidado en el ítem.
        if ((familyName == null || familyName.isBlank()) && hayVariations) {
            return familiaLegacy(item, productoId);
        }
        // NUEVO: family_name presente, o family_id persistido en el MLA.
        boolean esNuevo = (familyName != null && !familyName.isBlank())
                || (mla != null && mla.getFamilyId() != null && !mla.getFamilyId().isBlank());
        if (!esNuevo) return FamiliaMlDTO.ninguna();
        return familiaNueva(p, mla, familyName, item);
    }

    /** Familia del modelo nuevo: hermanos por family_id (BD) enriquecidos con eje/stock/status de ML. */
    private FamiliaMlDTO familiaNueva(Producto actual, Mla mla, String familyName, JsonNode itemActual) {
        List<Producto> hermanos = (mla != null && mla.getFamilyId() != null && !mla.getFamilyId().isBlank())
                ? productoRepository.findByMla_FamilyId(mla.getFamilyId())
                : List.of(actual);
        String categoryId = (itemActual != null) ? itemActual.path("category_id").asString(null) : null;
        List<MlAtributoDefDTO> ejeDefs = ejesDeCategoria(categoryId);
        Set<String> ejeIds = ejeDefs.stream().map(MlAtributoDefDTO::id).collect(Collectors.toSet());
        String ejeAtributoId = ejeDefs.isEmpty() ? null : ejeDefs.get(0).id();
        String ejeNombre = ejeDefs.isEmpty() ? null : ejeDefs.get(0).name();

        List<FamiliaVarianteDTO> variantes = new ArrayList<>();
        for (Producto h : hermanos) {
            String hMla = (h.getMla() != null) ? h.getMla().getMla() : null;
            Integer stock = null; String status = null; String ejeValor = null;
            try {
                JsonNode hi = h.getId().equals(actual.getId()) ? itemActual
                        : (hMla != null && !hMla.isBlank() ? mercadoLibreService.leerItemRaw(hMla) : null);
                if (hi != null) {
                    if (hi.path("available_quantity").isNumber()) stock = hi.path("available_quantity").asInt();
                    status = hi.path("status").asString(null);
                    ejeValor = valorEje(hi, ejeIds);
                }
            } catch (Exception ignored) { /* variante sin datos de ML */ }
            variantes.add(new FamiliaVarianteDTO(h.getId(), h.getSku(), tituloDe(h),
                    h.getId().equals(actual.getId()), ejeValor, stock, status, null));
        }
        variantes.sort(Comparator.comparing(FamiliaVarianteDTO::sku, Comparator.nullsLast(Comparator.naturalOrder())));
        return new FamiliaMlDTO("NUEVO", mla != null ? mla.getFamilyId() : null,
                familyName != null ? familyName : (mla != null ? mla.getFamilyName() : null), ejeAtributoId, ejeNombre, variantes);
    }

    /** Familia legacy: se arma del array variations[] del ítem. */
    private FamiliaMlDTO familiaLegacy(JsonNode item, Integer productoId) {
        List<MlAtributoDefDTO> ejeDefs = ejesDeCategoria(item.path("category_id").asString(null));
        String ejeAtributoId = ejeDefs.isEmpty() ? null : ejeDefs.get(0).id();
        String ejeNombre = ejeDefs.isEmpty() ? null : ejeDefs.get(0).name();
        String status = item.path("status").asString(null);
        List<FamiliaVarianteDTO> variantes = new ArrayList<>();
        for (JsonNode v : item.path("variations")) {
            Long variationId = v.path("id").isNumber() ? v.path("id").asLong() : null;
            Integer stock = v.path("available_quantity").isNumber() ? v.path("available_quantity").asInt() : null;
            String sku = sellerSku(v);
            String ejeValor = valorEjeCombinations(v);
            Integer prodId = (sku != null && !sku.isBlank())
                    ? productoRepository.findBySku(sku).map(Producto::getId).orElse(null) : null;
            variantes.add(new FamiliaVarianteDTO(prodId, sku, null,
                    prodId != null && prodId.equals(productoId), ejeValor, stock, status, variationId));
        }
        return new FamiliaMlDTO("LEGACY", null, item.path("title").asString(null), ejeAtributoId, ejeNombre, variantes);
    }

    /** Atributos de la categoría que permiten variación (allow_variations). Best-effort. */
    private List<MlAtributoDefDTO> ejesDeCategoria(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return List.of();
        try {
            return mlCategoriaAtributoService.obtenerAtributos(categoryId).stream()
                    .filter(MlAtributoDefDTO::allowVariations).toList();
        } catch (Exception e) { return List.of(); }
    }

    /** value_name del primer atributo del ítem cuyo id sea un eje (allow_variations). */
    private static String valorEje(JsonNode item, Set<String> ejeIds) {
        if (ejeIds.isEmpty()) return null;
        for (JsonNode a : item.path("attributes")) {
            if (ejeIds.contains(a.path("id").asString(null))) {
                String vn = a.path("value_name").asString(null);
                if (vn != null && !vn.isBlank()) return vn;
            }
        }
        return null;
    }

    /** value_name de la primera attribute_combinations de una variación legacy. */
    private static String valorEjeCombinations(JsonNode variation) {
        JsonNode comb = variation.path("attribute_combinations");
        if (comb.isArray() && !comb.isEmpty()) {
            String vn = comb.get(0).path("value_name").asString(null);
            if (vn != null && !vn.isBlank()) return vn;
        }
        return null;
    }

    /** SELLER_SKU (o seller_custom_field como fallback) de una variación legacy. */
    private static String sellerSku(JsonNode variation) {
        for (JsonNode a : variation.path("attributes")) {
            if ("SELLER_SKU".equals(a.path("id").asString(null))) {
                String vn = a.path("value_name").asString(null);
                if (vn != null && !vn.isBlank()) return vn.trim();
            }
        }
        String scf = variation.path("seller_custom_field").asString(null);
        return (scf != null && !scf.isBlank()) ? scf.trim() : null;
    }

    private static String tituloDe(Producto h) {
        return (h.getTituloNube() != null && !h.getTituloNube().isBlank()) ? h.getTituloNube() : h.getTituloDux();
    }

    /**
     * Quita un producto de su familia de variantes: PAUSA su publicación en ML (best-effort,
     * reversible) y lo DESASOCIA localmente (limpia family_id/family_name de su MLA). El producto y
     * su MLA no se borran. No-op si el producto no es de familia.
     * Supuesto: en el modelo nuevo (User Products) cada variante tiene su PROPIO MLA (1:1), así que
     * limpiar la familia del MLA afecta solo a esta variante. Si un MLA estuviera compartido por
     * varios productos, esto los desasociaría a todos.
     */
    @Transactional
    public void quitarDeFamilia(Integer productoId) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        Mla mla = p.getMla();
        if (mla == null || mla.getFamilyId() == null || mla.getFamilyId().isBlank()) return;
        try {
            if (mla.getMla() != null && !mla.getMla().isBlank()) mercadoLibreService.updateItemStatus(mla.getMla(), "paused");
        } catch (Exception ignored) { /* pausar es best-effort; igual desasociamos localmente */ }
        mla.setFamilyId(null);
        mla.setFamilyName(null);
        mlaRepository.save(mla);
    }

    /** Lee una tienda Nube (una sola GET reutilizada para estado, descripción, SEO y dims). Nunca lanza. */
    @Transactional(readOnly = true)
    public NubeCanalDTO leerNube(Integer id, String store) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String sku = p.getSku();
        JsonNode product;
        try {
            product = tiendaNubeService.buscarProductoPorSku(sku, store);
        } catch (Exception e) {
            return new NubeCanalDTO(EstadoCanalDTO.ofError(), null, null, null, null, null, null, null, null, null);
        }
        JsonNode variant = (product != null) ? product.path("variants").path(0) : null;
        String peso = variant != null ? variant.path("weight").asString(null) : null;
        String prof = variant != null ? variant.path("depth").asString(null) : null;
        String ancho = variant != null ? variant.path("width").asString(null) : null;
        String alto = variant != null ? variant.path("height").asString(null) : null;
        String titulo = (product != null) ? product.path("name").path("es").asString(null) : null;
        // id del producto en Nube (para armar el link "Editar en Tienda Nube"); distinto por tienda.
        Long productId = (product != null && product.path("id").isNumber()) ? product.path("id").asLong() : null;
        String ean = (variant != null) ? variant.path("barcode").asString(null) : null;
        EstadoCanalDTO estado = estadoNube(product);
        String descripcion = descripcionNube(product);
        SeoCanalDTO seo = NubeSeoParser.parse(product);
        return new NubeCanalDTO(estado, descripcion, seo, titulo, peso, prof, ancho, alto, productId, ean);
    }

    /** Lee Dux: findById → sku → DuxEstadoParser. Nunca lanza (ofError ante fallo). */
    @Transactional(readOnly = true)
    public DuxCanalDTO leerDux(Integer id) {
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String sku = p.getSku();
        try {
            Item item = duxService.obtenerProductoPorCodigo(sku);
            return new DuxCanalDTO(DuxEstadoParser.parse(item));
        } catch (Exception e) {
            return new DuxCanalDTO(EstadoCanalDTO.ofError());
        }
    }

    /** Resuelve el código MLA real por SKU contra la API de ML, en cualquier estado vigente (active/paused).
     *  Null si no hay publicación tradicional (las cerradas/borradas no las devuelve la búsqueda). */
    private String resolverMlaPorSku(String sku) {
        var hallado = mercadoLibreService.buscarMlaPorSkuCualquierEstado(sku);
        return hallado != null ? hallado.mla() : null;
    }

    private EstadoCanalDTO estadoNube(JsonNode product) {
        if (product == null) return EstadoCanalDTO.noPublicado();
        return NubeEstadoParser.parse(product);
    }

    private String descripcionNube(JsonNode product) {
        if (product == null) return null;
        return product.path("description").path("es").asString(null);
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
