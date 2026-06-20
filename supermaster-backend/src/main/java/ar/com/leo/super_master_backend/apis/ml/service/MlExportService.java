package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlExportResultDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlExportService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;
    private final MlaService mlaService;

    // Self-proxy: altaConProductoCargado debe correr en su propia transacción aunque
    // se la invoque desde exportar() (this.* no pasa por el proxy de Spring).
    @Lazy
    @Autowired
    private MlExportService self;

    public MlExportResultDTO exportar(MlExportRequestDTO request) {
        int creados = 0;
        List<String> actualizados = new ArrayList<>();
        List<String> yaExistian = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        if (request == null || request.skus() == null) {
            return new MlExportResultDTO(0, actualizados, yaExistian, errores, advertencias);
        }

        List<Producto> productos = productoRepository.findBySkuIn(
                request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

        for (Producto producto : productos) {
            Integer productoId = producto.getId();
            String etiqueta = producto.getSku();
            ResultadoAltaMl r = self.procesarConProductoCargado(productoId);
            switch (r.estado()) {
                case CREADO -> {
                    creados++;
                    List<String> avisos = new ArrayList<>();
                    if (r.advertencia() != null) avisos.add(r.advertencia());
                    avisos.addAll(postAlta(productoId, r.itemId(), r.mlau()));
                    for (String a : avisos) advertencias.add(etiqueta + ": " + a);
                }
                case ACTUALIZADO -> {
                    actualizados.add(etiqueta);
                    if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                }
                case YA_EXISTIA -> yaExistian.add(etiqueta);
                case ERROR -> errores.add(etiqueta + ": " + r.motivo());
            }
        }
        return new MlExportResultDTO(creados, actualizados, yaExistian, errores, advertencias);
    }

    /**
     * Carga el producto (managed, lazy abierto) y decide: si ya existe publicación en ML
     * (producto.getMla() o búsqueda por SKU) → actualizar; si no → alta.
     */
    @Transactional(readOnly = true)
    public ResultadoAltaMl procesarConProductoCargado(Integer productoId) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("Producto no encontrado");

        String mla = (p.getMla() != null) ? p.getMla().getMla() : null;
        if (mla == null) {
            var encontrado = mercadoLibreService.buscarMlaPorSku(p.getSku());
            if (encontrado != null) mla = encontrado.mla();
        }
        if (mla != null && !mla.isBlank()) {
            return mercadoLibreService.actualizarItemEnMl(p, mla);
        }
        return mercadoLibreService.crearItemEnMl(p);
    }

    /** Recarga el producto (managed) y hace el alta; la tx de lectura mantiene el lazy abierto. */
    @Transactional(readOnly = true)
    public ResultadoAltaMl altaConProductoCargado(Integer productoId) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("Producto no encontrado");
        return mercadoLibreService.crearItemEnMl(p);
    }

    /** Asocia el MLA y calcula comisión + envío. Cada paso es best-effort en su propia tx; devuelve avisos. */
    private List<String> postAlta(Integer productoId, String itemId, String mlau) {
        List<String> avisos = new ArrayList<>();
        try {
            mlaService.asegurarYAsociar(productoId, itemId, mlau);
        } catch (Exception e) {
            log.warn("ML - No se pudo asociar el MLA {} al producto {}: {}", itemId, productoId, e.getMessage());
            avisos.add("no se pudo asociar el MLA");
        }
        try {
            mercadoLibreService.obtenerCostoVenta(itemId);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular la comisión de {}: {}", itemId, e.getMessage());
            avisos.add("no se pudo calcular la comisión");
        }
        try {
            mercadoLibreService.calcularCostoEnvioGratis(itemId);
        } catch (Exception e) {
            log.warn("ML - No se pudo calcular el envío de {}: {}", itemId, e.getMessage());
            avisos.add("no se pudo calcular el envío");
        }
        return avisos;
    }
}
