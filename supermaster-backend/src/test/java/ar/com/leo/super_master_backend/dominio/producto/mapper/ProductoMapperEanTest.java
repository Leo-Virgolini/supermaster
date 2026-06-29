package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductoMapperEanTest {
    private final ProductoMapper mapper = Mappers.getMapper(ProductoMapper.class);

    @Test
    void create_copiaEan() {
        ProductoCreateDTO dto = new ProductoCreateDTO(
                "SKU-001",   // sku
                null,        // codExt
                "Titulo test", // tituloDux
                null,        // tituloMl
                null,        // tituloNube
                false,       // esCombo
                null,        // uxb
                null,        // moq
                null,        // stock
                true,        // activo
                null,        // marcaId
                null,        // origenId
                null,        // clasifGralId
                null,        // clasifGastroId
                1,           // tipoId
                null,        // proveedorId
                null,        // materialId
                null,        // mlaId
                null,        // sectorDepositoId
                null,        // capacidad
                null,        // largo
                null,        // ancho
                null,        // alto
                null,        // diamboca
                null,        // diambase
                null,        // espesor
                BigDecimal.ZERO, // costo
                new BigDecimal("21"), // iva
                null,        // tagReposicion
                null,        // tag
                null,        // mlPaqAlto
                null,        // mlPaqAncho
                null,        // mlPaqLargo
                null,        // mlPaqPeso
                "7791234567890" // ean
        );
        Producto p = mapper.toEntity(dto);
        assertThat(p.getEan()).isEqualTo("7791234567890");
    }
}
