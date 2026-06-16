package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.campania.dto.SincronizacionResultadoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CampaniaSyncService {

    /** Nombre del canal de precios de Tienda Nube. */
    public static final String CANAL_NUBE = "NUBE";

    private final TiendaNubeService tiendaNubeService;
    private final CanalRepository canalRepository;
    private final CampaniaRepository campaniaRepository;
    private final CampaniaProductoRepository campaniaProductoRepository;
    private final ProductoRepository productoRepository;

    @Transactional
    public SincronizacionResultadoDTO sincronizar(String storeName) {
        Canal canal = canalRepository.findByNombreIgnoreCase(CANAL_NUBE)
                .orElseThrow(() -> new NotFoundException("No existe el canal '" + CANAL_NUBE + "'"));

        Map<Long, String> categorias = tiendaNubeService.listarCategorias(storeName);
        Map<Long, List<String>> categoriaSkus = tiendaNubeService.mapearCategoriasASkus(storeName);

        LocalDateTime ahora = LocalDateTime.now();
        int productosVinculados = 0;
        LinkedHashSet<String> skusSinMatch = new LinkedHashSet<>();

        for (Map.Entry<Long, String> entry : categorias.entrySet()) {
            Long tnCategoriaId = entry.getKey();
            String nombre = entry.getValue();

            Campania campania = campaniaRepository.findByTnCategoriaId(tnCategoriaId)
                    .orElseGet(() -> {
                        Campania nueva = new Campania();
                        nueva.setTnCategoriaId(tnCategoriaId);
                        nueva.setCanal(canal);
                        nueva.setActiva(false);
                        return nueva;
                    });
            campania.setNombre(nombre);
            campania.setFechaUltimaSync(ahora);
            campania = campaniaRepository.save(campania);

            List<String> skus = categoriaSkus.getOrDefault(tnCategoriaId, List.of());
            if (skus.isEmpty()) {
                log.info("Campaña '{}' (tnCategoriaId={}) sin productos tageados en TN", nombre, tnCategoriaId);
            }
            productosVinculados += reconciliarProductos(campania, skus, ahora, skusSinMatch);
        }

        return new SincronizacionResultadoDTO(
                categorias.size(), productosVinculados, new ArrayList<>(skusSinMatch));
    }

    /**
     * Ajusta los productos de la campaña para que reflejen los SKUs tageados en TN.
     * Agrega los nuevos (precio en null), quita los que ya no están, y preserva los
     * que siguen (sin tocar su precioManual). Devuelve cuántos quedan vinculados.
     */
    private int reconciliarProductos(Campania campania, List<String> skus,
                                     LocalDateTime ahora, Set<String> skusSinMatch) {
        // SKU → Producto existente en la BD
        Map<String, Producto> porSku = new HashMap<>();
        if (!skus.isEmpty()) {
            List<String> distintos = skus.stream().distinct().toList();
            for (Producto p : productoRepository.findBySkuIn(distintos)) {
                porSku.put(p.getSku(), p);
            }
            for (String sku : distintos) {
                if (!porSku.containsKey(sku)) skusSinMatch.add(sku);
            }
        }

        // Ids de productos que deben estar en la campaña según TN
        Set<Integer> productoIdsDeseados = new LinkedHashSet<>();
        for (Producto p : porSku.values()) productoIdsDeseados.add(p.getId());

        // Estado actual en la BD
        List<CampaniaProducto> actuales = campaniaProductoRepository.findByCampaniaId(campania.getId());
        Set<Integer> productoIdsActuales = new HashSet<>();
        for (CampaniaProducto cp : actuales) {
            Integer pid = cp.getProducto().getId();
            if (productoIdsDeseados.contains(pid)) {
                productoIdsActuales.add(pid); // sigue → preservar (no tocar)
            } else {
                campaniaProductoRepository.delete(cp); // destageado → quitar
            }
        }

        // Agregar los nuevos
        for (Producto p : porSku.values()) {
            if (productoIdsActuales.contains(p.getId())) continue;
            CampaniaProducto cp = new CampaniaProducto();
            cp.setCampania(campania);
            cp.setProducto(p);
            cp.setFechaSync(ahora);
            campaniaProductoRepository.save(cp);
        }

        return productoIdsDeseados.size();
    }
}
