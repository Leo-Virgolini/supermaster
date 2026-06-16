package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ProductoMargenValidacionTest {

    @Mock private ProductoMargenRepository repo;
    @Mock private ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMargenMapper mapper;
    @Mock private ProductoRepository productoRepository;
    @Mock private ar.com.leo.super_master_backend.dominio.common.service.RecalculoPendienteService recalculoPendienteService;
    @Mock private ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService auditoriaService;

    @InjectMocks private ProductoMargenServiceImpl service;

    @Test
    @DisplayName("guardar con ambos márgenes en 0 lanza BadRequestException")
    void ambosCeroRebota() {
        Producto p = new Producto(1);
        p.setSku("X");
        lenient().when(productoRepository.findById(1)).thenReturn(Optional.of(p));
        lenient().when(repo.findByProductoId(1)).thenReturn(Optional.empty());

        ProductoMargenDTO dto = new ProductoMargenDTO(
                null, 1, BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("margen");
    }
}
