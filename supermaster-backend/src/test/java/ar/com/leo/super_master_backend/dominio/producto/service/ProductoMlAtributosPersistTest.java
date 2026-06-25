package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.material.repository.MaterialRepository;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMlAtributoDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMlAtributo;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de persistencia para la relación Producto → ProductoMlAtributo.
 * Verifica que el reemplazo total del set (clear + re-add) funciona correctamente.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductoMlAtributosPersistTest {

    @Autowired private ProductoRepository productoRepository;
    @Autowired private OrigenRepository origenRepository;
    @Autowired private TipoRepository tipoRepository;
    @Autowired private ClasifGralRepository clasifGralRepository;
    @Autowired private MarcaRepository marcaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private MaterialRepository materialRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String TEST_PREFIX = "ZTEST_PMA_";

    private Producto producto;

    @BeforeEach
    void setUp() {
        Origen origen = origenRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Origen o = new Origen();
                    o.setNombre(TEST_PREFIX + "Origen");
                    return origenRepository.save(o);
                });

        Tipo tipo = tipoRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Tipo t = new Tipo();
                    t.setNombre(TEST_PREFIX + "Tipo");
                    return tipoRepository.save(t);
                });

        ClasifGral clasifGral = clasifGralRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    ClasifGral c = new ClasifGral();
                    c.setNombre(TEST_PREFIX + "ClasifGral");
                    return clasifGralRepository.save(c);
                });

        Marca marca = marcaRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Marca m = new Marca();
                    m.setNombre(TEST_PREFIX + "Marca");
                    return marcaRepository.save(m);
                });

        Proveedor proveedor = proveedorRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Proveedor p = new Proveedor();
                    p.setNombre(TEST_PREFIX + "Proveedor");
                    return proveedorRepository.save(p);
                });

        Material material = materialRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Material mat = new Material();
                    mat.setNombre(TEST_PREFIX + "Material");
                    return materialRepository.save(mat);
                });

        producto = new Producto();
        producto.setSku(TEST_PREFIX + "001");
        producto.setTituloDux(TEST_PREFIX + "Producto de prueba");
        producto.setCosto(new BigDecimal("1000"));
        producto.setIva(new BigDecimal("21"));
        producto.setOrigen(origen);
        producto.setTipo(tipo);
        producto.setClasifGral(clasifGral);
        producto.setMarca(marca);
        producto.setProveedor(proveedor);
        producto.setMaterial(material);
        producto.setTag(Tag.MENAJE);
        producto = productoRepository.save(producto);
        entityManager.flush();
    }

    /**
     * Aplica la lógica de reemplazo total del set (equivalente a lo que hace ProductoServiceImpl).
     * Flush tras el clear() para que Hibernate ejecute los DELETEs ANTES de los INSERTs,
     * evitando violación del unique index (id_producto, attribute_id).
     */
    private void reemplazarAtributos(Producto p, List<ProductoMlAtributoDTO> dtos) {
        p.getMlAtributos().clear();
        entityManager.flush(); // fuerza DELETEs antes de los INSERTs
        if (dtos != null) {
            for (ProductoMlAtributoDTO a : dtos) {
                ProductoMlAtributo e = new ProductoMlAtributo();
                e.setProducto(p);
                e.setAttributeId(a.attributeId());
                e.setValueId((a.valueId() == null || a.valueId().isBlank()) ? null : a.valueId());
                e.setValueName(a.valueName());
                p.getMlAtributos().add(e);
            }
        }
        productoRepository.save(p);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void crear_con2Atributos_persiste2Filas() {
        reemplazarAtributos(producto, List.of(
                new ProductoMlAtributoDTO("SALE_FORMAT", "242073", "Unidad"),
                new ProductoMlAtributoDTO("BRAND", null, "Tramontina")
        ));

        Producto guardado = productoRepository.findById(producto.getId()).orElseThrow();
        Set<ProductoMlAtributo> atributos = guardado.getMlAtributos();

        assertThat(atributos).hasSize(2);
        assertThat(atributos).extracting(ProductoMlAtributo::getAttributeId)
                .containsExactlyInAnyOrder("SALE_FORMAT", "BRAND");
    }

    @Test
    void actualizarCon1Atributo_reemplazaSet_queda1Fila() {
        // Primero guardamos 2 atributos
        reemplazarAtributos(producto, List.of(
                new ProductoMlAtributoDTO("SALE_FORMAT", "242073", "Unidad"),
                new ProductoMlAtributoDTO("BRAND", null, "Tramontina")
        ));

        // Recargamos el producto tras el entityManager.clear()
        Producto p2 = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(p2.getMlAtributos()).hasSize(2);

        // Ahora reemplazamos con solo 1 atributo
        reemplazarAtributos(p2, List.of(
                new ProductoMlAtributoDTO("SALE_FORMAT", null, "Unidad")
        ));

        Producto guardado = productoRepository.findById(producto.getId()).orElseThrow();
        assertThat(guardado.getMlAtributos()).extracting(ProductoMlAtributo::getAttributeId)
                .containsExactlyInAnyOrder("SALE_FORMAT");
    }

    @Test
    void valueIdVacio_seGuardaComoNull() {
        reemplazarAtributos(producto, List.of(
                new ProductoMlAtributoDTO("BRAND", "", "Tramontina")
        ));

        Producto guardado = productoRepository.findById(producto.getId()).orElseThrow();
        ProductoMlAtributo attr = guardado.getMlAtributos().iterator().next();

        assertThat(attr.getValueId()).isNull();
        assertThat(attr.getValueName()).isEqualTo("Tramontina");
    }
}
