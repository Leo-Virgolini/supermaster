package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.dominio.common.dto.ExportCanalResultDTO;
import ar.com.leo.super_master_backend.dominio.common.export.ExportResultAcumulador;
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

    // Self-proxy: procesarConProductoCargado debe correr en su propia transacción aunque
    // se la invoque desde exportar() (this.* no pasa por el proxy de Spring).
    @Lazy
    @Autowired
    private MlExportService self;

    public ExportCanalResultDTO exportar(MlExportRequestDTO request) {
        if (request == null || request.skus() == null) {
            return new ExportResultAcumulador().toDTO();
        }

        ExportResultAcumulador acc = new ExportResultAcumulador();

        List<Producto> productos = productoRepository.findBySkuIn(
                ExportResultAcumulador.normalizarSkus(request.skus()));

        for (Producto producto : productos) {
            Integer productoId = producto.getId();
            String etiqueta = producto.getSku();
            ResultadoAltaMl r = self.procesarConProductoCargado(
                    productoId, request.cuotas(), request.mlCategoryId(), request.mlAtributos(), request.descripcionMl());
            switch (r.estado()) {
                case CREADO -> {
                    acc.creado();
                    List<String> avisos = new ArrayList<>();
                    if (r.advertencia() != null) avisos.add(r.advertencia());
                    avisos.addAll(postAlta(productoId, r.itemId(), r.mlau()));
                    for (String a : avisos) acc.advertencia(etiqueta + ": " + a);
                }
                case ACTUALIZADO -> {
                    acc.actualizado(etiqueta);
                    if (r.advertencia() != null) acc.advertencia(etiqueta + ": " + r.advertencia());
                    if (r.mlau() != null) {
                        try {
                            mlaService.asegurarYAsociar(productoId, r.itemId(), r.mlau());
                        } catch (Exception e) {
                            log.warn("ML - No se pudo asociar el MLA {} al producto {}: {}", r.itemId(), productoId, e.getMessage());
                            acc.advertencia(etiqueta + ": no se pudo asociar el MLA");
                        }
                    }
                }
                case YA_EXISTIA -> acc.yaExistia(etiqueta);
                case ERROR -> acc.error(etiqueta + ": " + r.motivo());
            }
        }
        return acc.toDTO();
    }

    /**
     * Carga el producto (managed, lazy abierto) y decide: si ya existe publicación en ML
     * (producto.getMla() o búsqueda por SKU) → actualizar; si no → alta.
     */
    @Transactional(readOnly = true)
    public ResultadoAltaMl procesarConProductoCargado(Integer productoId, int cuotas,
                                                      String mlCategoryId,
                                                      java.util.List<ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO> mlAtributos,
                                                      String descripcionMl) {
        Producto p = productoRepository.findById(productoId).orElse(null);
        if (p == null) return ResultadoAltaMl.error("producto no encontrado");

        // Datos de canal transitorios (no persistidos): los usa el publish. En lote llegan null.
        if (mlCategoryId != null && !mlCategoryId.isBlank()) p.setMlCategoryId(mlCategoryId);
        if (mlAtributos != null) p.setMlAtributos(mlAtributos);
        if (descripcionMl != null) p.setDescripcionMl(descripcionMl);

        String mla = (p.getMla() != null) ? p.getMla().getMla() : null;
        String mlauHallado = null;
        if (mla == null) {
            var encontrado = mercadoLibreService.buscarMlaPorSku(p.getSku());
            if (encontrado != null) { mla = encontrado.mla(); mlauHallado = encontrado.mlau(); }
        }
        if (mla != null && !mla.isBlank()) {
            ResultadoAltaMl r = mercadoLibreService.actualizarItemEnMl(p, mla, cuotas);
            // Si el MLA lo hallamos por búsqueda (no estaba asociado) y el update fue OK,
            // adjuntar el mlau para que exportar lo asocie al producto.
            if (mlauHallado != null && r.estado() == ResultadoAltaMl.Estado.ACTUALIZADO) {
                ResultadoAltaMl conMlau = ResultadoAltaMl.actualizado(mla, mlauHallado);
                return r.advertencia() != null ? conMlau.conAdvertencia(r.advertencia()) : conMlau;
            }
            return r;
        }
        return mercadoLibreService.crearItemEnMl(p, cuotas);
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
