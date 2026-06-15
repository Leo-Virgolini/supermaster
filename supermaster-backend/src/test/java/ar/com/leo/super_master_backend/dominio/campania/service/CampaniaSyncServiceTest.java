package ar.com.leo.super_master_backend.dominio.campania.service;

import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.campania.dto.SincronizacionResultadoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaProductoRepository;
import ar.com.leo.super_master_backend.dominio.campania.repository.CampaniaRepository;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaniaSyncServiceTest {

    @Mock private TiendaNubeService tiendaNubeService;
    @Mock private CanalRepository canalRepository;
    @Mock private CampaniaRepository campaniaRepository;
    @Mock private CampaniaProductoRepository campaniaProductoRepository;
    @Mock private ProductoRepository productoRepository;

    private CampaniaSyncService service;

    private Canal canalNube;

    @BeforeEach
    void setUp() {
        service = new CampaniaSyncService(
                tiendaNubeService, canalRepository, campaniaRepository,
                campaniaProductoRepository, productoRepository);
        canalNube = new Canal(7);
        canalNube.setNombre("NUBE");
        when(canalRepository.findByNombreIgnoreCase("NUBE")).thenReturn(Optional.of(canalNube));
    }

    private Producto producto(int id, String sku) {
        Producto p = new Producto();
        p.setId(id);
        p.setSku(sku);
        return p;
    }

    @Test
    @DisplayName("categoría nueva → crea campania con activa=false y vincula sus productos")
    void categoriaNueva_creaCampaniaYVincula() {
        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1", "SKU2")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.empty());
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> {
            Campania c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1);
            return c;
        });
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of());
        when(productoRepository.findBySkuIn(List.of("SKU1", "SKU2")))
                .thenReturn(List.of(producto(11, "SKU1"), producto(12, "SKU2")));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.categoriasImportadas()).isEqualTo(1);
        assertThat(r.productosVinculados()).isEqualTo(2);
        assertThat(r.skusSinMatch()).isEmpty();

        ArgumentCaptor<Campania> campCaptor = ArgumentCaptor.forClass(Campania.class);
        verify(campaniaRepository, atLeastOnce()).save(campCaptor.capture());
        assertThat(campCaptor.getAllValues().get(0).getActiva()).isFalse();
        assertThat(campCaptor.getAllValues().get(0).getNombre()).isEqualTo("Día del Padre");

        verify(campaniaProductoRepository, times(2)).save(any(CampaniaProducto.class));
    }

    @Test
    @DisplayName("campaña existente → actualiza nombre y preserva precioManual de productos que siguen")
    void campaniaExistente_preservaPrecio() {
        Campania existente = new Campania(1);
        existente.setTnCategoriaId(100L);
        existente.setNombre("Viejo nombre");
        existente.setCanal(canalNube);

        Producto p1 = producto(11, "SKU1");
        CampaniaProducto cpExistente = new CampaniaProducto();
        cpExistente.setId(50);
        cpExistente.setCampania(existente);
        cpExistente.setProducto(p1);
        cpExistente.setPrecioManual(new BigDecimal("999.00"));

        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.of(existente));
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of(cpExistente));
        when(productoRepository.findBySkuIn(List.of("SKU1"))).thenReturn(List.of(p1));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isEqualTo(1);
        assertThat(existente.getNombre()).isEqualTo("Día del Padre");
        // No se borra ni recrea la fila existente → precio intacto
        verify(campaniaProductoRepository, never()).delete(any(CampaniaProducto.class));
        verify(campaniaProductoRepository, never()).save(any(CampaniaProducto.class));
        assertThat(cpExistente.getPrecioManual()).isEqualByComparingTo("999.00");
    }

    @Test
    @DisplayName("producto destageado en TN → se quita de la campaña")
    void productoDestageado_seQuita() {
        Campania existente = new Campania(1);
        existente.setTnCategoriaId(100L);
        existente.setNombre("Día del Padre");
        existente.setCanal(canalNube);

        Producto p1 = producto(11, "SKU1");
        CampaniaProducto cpViejo = new CampaniaProducto();
        cpViejo.setId(50);
        cpViejo.setCampania(existente);
        cpViejo.setProducto(p1);

        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of()));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.of(existente));
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of(cpViejo));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isZero();
        verify(campaniaProductoRepository).delete(cpViejo);
    }

    @Test
    @DisplayName("SKU tageado en TN sin match en la BD → se reporta y se omite")
    void skuSinMatch_seReporta() {
        when(tiendaNubeService.listarCategorias("KT HOGAR")).thenReturn(Map.of(100L, "Día del Padre"));
        when(tiendaNubeService.mapearCategoriasASkus("KT HOGAR")).thenReturn(Map.of(100L, List.of("SKU1", "FANTASMA")));
        when(campaniaRepository.findByTnCategoriaId(100L)).thenReturn(Optional.empty());
        when(campaniaRepository.save(any(Campania.class))).thenAnswer(inv -> {
            Campania c = inv.getArgument(0);
            if (c.getId() == null) c.setId(1);
            return c;
        });
        when(campaniaProductoRepository.findByCampaniaId(1)).thenReturn(List.of());
        when(productoRepository.findBySkuIn(List.of("SKU1", "FANTASMA")))
                .thenReturn(List.of(producto(11, "SKU1")));

        SincronizacionResultadoDTO r = service.sincronizar("KT HOGAR");

        assertThat(r.productosVinculados()).isEqualTo(1);
        assertThat(r.skusSinMatch()).containsExactly("FANTASMA");
    }
}
