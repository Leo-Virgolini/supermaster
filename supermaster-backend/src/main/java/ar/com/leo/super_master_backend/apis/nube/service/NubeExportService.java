package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeRequestDTO;
import ar.com.leo.super_master_backend.apis.nube.dto.ExportNubeResultDTO;
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

import java.util.ArrayList;
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
    public ExportNubeResultDTO exportar(ExportNubeRequestDTO request) {
        int creados = 0;
        List<String> yaExistian = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        if (request == null || request.skus() == null || request.tiendas() == null) {
            return new ExportNubeResultDTO(0, yaExistian, errores, advertencias);
        }

        List<Producto> productos = productoRepository.findBySkuIn(
                request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

        // Árbol de categorías por tienda, cargado una vez por corrida (lazy por tienda usada).
        Map<String, NubeCategoriaArbol> arbolesPorTienda = new HashMap<>();

        for (Producto producto : productos) {
            for (ExportNubeRequestDTO.DestinoNube destino : request.tiendas()) {
                String tienda = destino.tienda();
                String etiqueta = producto.getSku() + " / " + tienda;
                Optional<Canal> canal = canalRepository.findByNombreIgnoreCase(tienda);
                if (canal.isEmpty()) { errores.add(etiqueta + ": canal '" + tienda + "' no existe"); continue; }
                Optional<ProductoCanalPrecio> precio = precioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.get().getId(), destino.cuotas());
                if (precio.isEmpty()) { errores.add(etiqueta + ": sin precio calculado para esa cuota"); continue; }
                if (precio.get().isObsoleto()) { errores.add(etiqueta + ": precio desactualizado (recalcular antes de subir)"); continue; }

                NubeCategoriaArbol arbol = arbolesPorTienda.computeIfAbsent(
                        tienda, tiendaNubeService::cargarArbolCategorias);
                ResultadoAltaNube r = tiendaNubeService.crearProductoEnNube(
                        tienda, producto, precio.get().getPvp(), precio.get().getPvpInflado(), arbol);
                switch (r.estado()) {
                    case CREADO -> {
                        creados++;
                        if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                    }
                    case YA_EXISTIA -> yaExistian.add(etiqueta);
                    case ERROR -> errores.add(etiqueta + ": " + r.motivo());
                }
            }
        }
        return new ExportNubeResultDTO(creados, yaExistian, errores, advertencias);
    }
}
