package ar.com.leo.super_master_backend.apis.ml.service;

import ar.com.leo.super_master_backend.apis.ml.dto.MlExportRequestDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.MlExportResultDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MlExportService {

    private final ProductoRepository productoRepository;
    private final MercadoLibreService mercadoLibreService;

    /**
     * Necesita sesión JPA abierta durante el loop: el alta accede a asociaciones LAZY del Producto
     * (marca, material, aptos) con open-in-view=false. El I/O de red contra ML ocurre dentro de la
     * transacción; aceptable para este export manual de bajo volumen.
     */
    @Transactional(readOnly = true)
    public MlExportResultDTO exportar(MlExportRequestDTO request) {
        int creados = 0;
        List<String> yaExistian = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        if (request == null || request.skus() == null) {
            return new MlExportResultDTO(0, yaExistian, errores, advertencias);
        }

        List<Producto> productos = productoRepository.findBySkuIn(
                request.skus().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList());

        for (Producto producto : productos) {
            String etiqueta = producto.getSku();
            ResultadoAltaMl r = mercadoLibreService.crearItemEnMl(producto);
            switch (r.estado()) {
                case CREADO -> {
                    creados++;
                    if (r.advertencia() != null) advertencias.add(etiqueta + ": " + r.advertencia());
                }
                case YA_EXISTIA -> yaExistian.add(etiqueta);
                case ERROR -> errores.add(etiqueta + ": " + r.motivo());
            }
        }
        return new MlExportResultDTO(creados, yaExistian, errores, advertencias);
    }
}
