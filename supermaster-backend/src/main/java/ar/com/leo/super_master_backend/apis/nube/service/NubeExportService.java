package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeRequestDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import ar.com.leo.super_master_backend.dominio.common.export.ExportResultAcumulador;
import ar.com.leo.super_master_backend.apis.nube.dto.ResultadoAltaNube;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NubeExportService {

    private final ProductoRepository productoRepository;
    private final ProductoCanalPrecioRepository precioRepository;
    private final CanalRepository canalRepository;
    private final TiendaNubeService tiendaNubeService;

    /**
     * Necesita sesión JPA abierta durante todo el loop porque el código accede a asociaciones
     * LAZY (clasifGral, clasifGastro, tipo) y recorre recursivamente la cadena getPadre() de
     * NubeCategoriaRuta, con open-in-view=false. @Transactional(readOnly=true) mantiene la
     * sesión viva sin overhead de escritura. El I/O de red contra Tienda Nube ocurre dentro de
     * la transacción; es aceptable para este export manual de bajo volumen.
     */
    @Transactional(readOnly = true)
    public ExportCanalResultDTO exportar(ExportNubeRequestDTO request) {
        if (request == null || request.skus() == null || request.tiendas() == null) {
            return new ExportResultAcumulador().toDTO();
        }

        ExportResultAcumulador acc = new ExportResultAcumulador();

        List<Producto> productos = productoRepository.findBySkuIn(
                ExportResultAcumulador.normalizarSkus(request.skus()));

        Map<String, NubeCategoriaArbol> arbolesPorTienda = new HashMap<>();

        for (Producto producto : productos) {
            for (ExportNubeRequestDTO.DestinoNube destino : request.tiendas()) {
                String tienda = destino.tienda();
                String etiqueta = producto.getSku() + " / " + tienda;
                Optional<Canal> canal = canalRepository.findByNombreIgnoreCase(tienda);
                if (canal.isEmpty()) { acc.error(etiqueta + ": canal '" + tienda + "' no existe"); continue; }
                Optional<ProductoCanalPrecio> precio = precioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.get().getId(), destino.cuotas());
                if (precio.isEmpty()) { acc.error(etiqueta + ": sin precio calculado para esa cuota"); continue; }
                if (precio.get().isObsoleto()) { acc.error(etiqueta + ": precio desactualizado (recalcular antes de subir)"); continue; }

                // Descripción transitoria de esta tienda (no persistida; en lote llega null y el publish la omite).
                producto.setDescripcionNube(destino.descripcion());

                // Upsert: si ya existe en la tienda, actualizar; si no, crear.
                ResultadoAltaNube r;
                JsonNode existenteEnNube = tiendaNubeService.buscarProductoPorSku(producto.getSku(), tienda);
                NubeCategoriaArbol arbol = arbolesPorTienda.computeIfAbsent(
                        tienda, tiendaNubeService::cargarArbolCategorias);
                if (existenteEnNube != null) {
                    List<Long> categoriaIds = tiendaNubeService.resolverCategoriaIds(tienda, producto, arbol);
                    r = tiendaNubeService.actualizarProductoEnNube(
                            tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), existenteEnNube, categoriaIds, destino.seo());
                } else {
                    r = tiendaNubeService.crearProductoEnNube(
                            tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), arbol, destino.seo());
                }
                switch (r.estado()) {
                    case CREADO -> {
                        acc.creado();
                        if (r.advertencia() != null) acc.advertencia(etiqueta + ": " + r.advertencia());
                    }
                    case ACTUALIZADO -> {
                        acc.actualizado(etiqueta);
                        if (r.advertencia() != null) acc.advertencia(etiqueta + ": " + r.advertencia());
                    }
                    case YA_EXISTIA -> acc.yaExistia(etiqueta);
                    case ERROR -> acc.error(etiqueta + ": " + r.motivo());
                }
            }
        }
        return acc.toDTO();
    }
}
