package ar.com.leo.super_master_backend.apis.ml;

import ar.com.leo.super_master_backend.apis.ml.dto.ResultadoAltaMl;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.ml.service.MlExportService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MlExportServiceCuotaTest {

    private final ProductoRepository productoRepository = mock(ProductoRepository.class);
    private final MercadoLibreService mercadoLibreService = mock(MercadoLibreService.class);
    private final MlaService mlaService = mock(MlaService.class);
    private final MlExportService service = new MlExportService(productoRepository, mercadoLibreService, mlaService);

    @Test
    void sinPublicacion_propagaLaCuotaAlAlta() {
        Producto p = new Producto();
        p.setId(1);
        p.setSku("SKU1");
        when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        when(mercadoLibreService.buscarMlaPorSku("SKU1")).thenReturn(null);
        when(mercadoLibreService.crearItemEnMl(p, 6)).thenReturn(ResultadoAltaMl.error("irrelevante"));

        service.procesarConProductoCargado(1, 6, null, null, null, null);

        verify(mercadoLibreService).crearItemEnMl(p, 6);
        verify(mercadoLibreService, never()).crearItemEnMl(any(), eq(0));
    }

    @Test
    void conPublicacion_propagaLaCuotaAlUpdate() {
        Producto p = mock(Producto.class);
        Mla mla = mock(Mla.class);
        when(p.getId()).thenReturn(1);
        when(p.getMla()).thenReturn(mla);
        when(mla.getMla()).thenReturn("MLA123");
        when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        when(mercadoLibreService.actualizarItemEnMl(p, "MLA123", 3))
                .thenReturn(ResultadoAltaMl.actualizado("MLA123", null));

        service.procesarConProductoCargado(1, 3, null, null, null, null);

        verify(mercadoLibreService).actualizarItemEnMl(p, "MLA123", 3);
        verify(mercadoLibreService, never()).actualizarItemEnMl(any(), any(), eq(0));
    }
}
