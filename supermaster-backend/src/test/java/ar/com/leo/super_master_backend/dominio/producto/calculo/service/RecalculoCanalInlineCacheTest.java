package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.*;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoReglaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el caché de contexto de canal usado por el recálculo inline:
 *  1. EQUIVALENCIA: recalcular con el caché activo produce exactamente los mismos
 *     valores que el camino normal a BD (el caché solo cambia de dónde vienen los datos).
 *  2. DETACHED-SAFETY: tras un {@code em.clear()} (que el recálculo real hace cada 50
 *     productos), las entidades del caché quedan detached pero el cálculo sigue funcionando
 *     sin {@code LazyInitializationException} y persiste precios nuevos vía {@code getReference}.
 *
 * No se invoca {@code recalcularCanalCompletoInline} (iteraría todo el catálogo real);
 * se ejercita el caché directamente con un canal/producto de prueba (rollback al final).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecalculoCanalInlineCacheTest {

    @Autowired private OrigenRepository origenRepository;
    @Autowired private TipoRepository tipoRepository;
    @Autowired private ClasifGralRepository clasifGralRepository;
    @Autowired private CanalRepository canalRepository;
    @Autowired private ConceptoCalculoRepository conceptoCalculoRepository;
    @Autowired private CanalConceptoRepository canalConceptoRepository;
    @Autowired private CanalConceptoReglaRepository canalConceptoReglaRepository;
    @Autowired private CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoMargenRepository productoMargenRepository;
    @Autowired private ProductoCanalPrecioRepository productoCanalPrecioRepository;
    @Autowired private CalculoPrecioService calculoPrecioService;

    @PersistenceContext private EntityManager entityManager;

    private static final String P = "ZTESTCACHE_";

    private Producto producto;
    private Canal canal;

    @BeforeEach
    void setUp() {
        Origen origen = origenRepository.findAll().stream().findFirst().orElseGet(() -> {
            Origen o = new Origen(); o.setNombre(P + "Origen"); return origenRepository.save(o);
        });
        Tipo tipo = tipoRepository.findAll().stream().findFirst().orElseGet(() -> {
            Tipo t = new Tipo(); t.setNombre(P + "Tipo"); return tipoRepository.save(t);
        });
        ClasifGral clasifGral = clasifGralRepository.findAll().stream().findFirst().orElseGet(() -> {
            ClasifGral c = new ClasifGral(); c.setNombre(P + "ClasifGral"); return clasifGralRepository.save(c);
        });

        canal = new Canal();
        canal.setNombre(P + "CANAL");
        canal = canalRepository.save(canal);

        ConceptoCalculo conceptoMargen = new ConceptoCalculo();
        conceptoMargen.setNombre(P + "MARGEN_MIN");
        conceptoMargen.setPorcentaje(BigDecimal.ZERO);
        conceptoMargen.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);
        conceptoMargen = conceptoCalculoRepository.save(conceptoMargen);

        ConceptoCalculo conceptoComision = new ConceptoCalculo();
        conceptoComision.setNombre(P + "COMISION");
        conceptoComision.setPorcentaje(new BigDecimal("10"));
        conceptoComision.setAplicaSobre(AplicaSobre.COMISION_SOBRE_PVP);
        conceptoComision = conceptoCalculoRepository.save(conceptoComision);

        CanalConcepto ccMargen = new CanalConcepto();
        ccMargen.setId(new CanalConceptoId(canal.getId(), conceptoMargen.getId()));
        ccMargen.setCanal(canal);
        ccMargen.setConcepto(conceptoMargen);
        canalConceptoRepository.save(ccMargen);

        CanalConcepto ccComision = new CanalConcepto();
        ccComision.setId(new CanalConceptoId(canal.getId(), conceptoComision.getId()));
        ccComision.setCanal(canal);
        ccComision.setConcepto(conceptoComision);
        canalConceptoRepository.save(ccComision);

        // Regla de concepto con condición de TIPO (relación LAZY): ejercita
        // cumpleCondicionesRegla accediendo regla.getTipo()/getConcepto() sobre una
        // CanalConceptoRegla que queda DETACHED tras el em.clear() del recálculo con caché.
        // Tipo distinto al del producto → la regla EXCLUIR no matchea (el concepto igual aplica),
        // así el resultado no cambia y la equivalencia se mantiene.
        Tipo otroTipo = new Tipo();
        otroTipo.setNombre(P + "TipoRegla");
        otroTipo = tipoRepository.save(otroTipo);

        CanalConceptoRegla regla = new CanalConceptoRegla();
        regla.setCanal(canal);
        regla.setConcepto(conceptoComision);
        regla.setTipoRegla(TipoRegla.EXCLUIR);
        regla.setTipo(otroTipo);
        canalConceptoReglaRepository.save(regla);

        // Dos cuotas para ejercitar el loop multi-cuota del recálculo.
        CanalConceptoCuota contado = new CanalConceptoCuota();
        contado.setCanal(canal);
        contado.setCuotas(1);
        contado.setPorcentaje(BigDecimal.ZERO);
        contado.setDescripcion("Contado");
        canalConceptoCuotaRepository.save(contado);

        CanalConceptoCuota tres = new CanalConceptoCuota();
        tres.setCanal(canal);
        tres.setCuotas(3);
        tres.setPorcentaje(new BigDecimal("15"));
        tres.setDescripcion("3 cuotas");
        canalConceptoCuotaRepository.save(tres);

        producto = new Producto();
        producto.setSku(P + "001");
        producto.setTituloDux(P + "Producto");
        producto.setTituloNube(P + "Producto");
        producto.setCosto(new BigDecimal("1000"));
        producto.setIva(new BigDecimal("21"));
        producto.setOrigen(origen);
        producto.setTipo(tipo);
        producto.setClasifGral(clasifGral);
        producto = productoRepository.save(producto);

        ProductoMargen margen = new ProductoMargen();
        margen.setProducto(producto);
        margen.setMargenMinorista(new BigDecimal("50"));
        margen.setMargenMayorista(new BigDecimal("30"));
        productoMargenRepository.save(margen);

        entityManager.flush();
    }

    @Test
    @DisplayName("caché inline: mismos precios que el camino a BD, y resiste em.clear() (detached)")
    void cacheInline_equivalente_y_detachedSafe() {
        // 1) Camino normal (sin caché): capturar PVP esperado por cuota.
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        entityManager.flush();
        entityManager.clear();
        BigDecimal pvp1Esperado = pvp(1);
        BigDecimal pvp3Esperado = pvp(3);
        assertThat(pvp1Esperado).isNotNull();
        assertThat(pvp3Esperado).isNotNull();

        // 2) Con el caché activo + em.clear() en el medio (simula el flush/clear cada 50
        //    productos del recálculo real): el caché queda detached pero debe seguir
        //    sirviendo el cálculo sin LazyInitializationException.
        calculoPrecioService.iniciarCacheContextoCanal(canal.getId());
        try {
            entityManager.clear(); // detacha el contexto cacheado
            calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
            entityManager.flush();
        } finally {
            calculoPrecioService.limpiarCacheContextoCanal();
        }
        entityManager.clear();

        // 3) Equivalencia exacta.
        assertThat(pvp(1)).isEqualByComparingTo(pvp1Esperado);
        assertThat(pvp(3)).isEqualByComparingTo(pvp3Esperado);
    }

    @Test
    @DisplayName("recalcularCanalCompletoBatch: mismos valores que el camino producto-por-producto")
    void batchEquivalenteAlInline() {
        // Camino viejo (producto-por-producto) para el producto de prueba → valores esperados.
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        entityManager.flush();
        entityManager.clear();
        java.util.Map<Integer, BigDecimal[]> esperado = snapshotProducto();
        assertThat(esperado).containsKeys(1, 3);

        // Borrar las filas del canal y recalcular con el camino batch (canal base).
        productoCanalPrecioRepository.deleteAll(productoCanalPrecioRepository.findByCanalId(canal.getId()));
        entityManager.flush();
        entityManager.clear();

        calculoPrecioService.recalcularCanalCompletoBatch(canal.getId(), null);
        entityManager.flush();
        entityManager.clear();
        java.util.Map<Integer, BigDecimal[]> actual = snapshotProducto();

        // Mismas cuotas y mismos valores en cada campo numérico.
        assertThat(actual.keySet()).isEqualTo(esperado.keySet());
        for (Integer cuota : esperado.keySet()) {
            BigDecimal[] e = esperado.get(cuota);
            BigDecimal[] a = actual.get(cuota);
            for (int i = 0; i < e.length; i++) {
                if (e[i] == null) {
                    assertThat(a[i]).as("cuota %s, campo %s", cuota, i).isNull();
                } else {
                    assertThat(a[i]).as("cuota %s, campo %s", cuota, i).isEqualByComparingTo(e[i]);
                }
            }
        }
    }

    /** Snapshot de los campos numéricos del producto de prueba, por cuota. */
    private java.util.Map<Integer, BigDecimal[]> snapshotProducto() {
        java.util.Map<Integer, BigDecimal[]> map = new java.util.HashMap<>();
        for (ProductoCanalPrecio p : productoCanalPrecioRepository
                .findByProductoIdAndCanalIdOrderByCuotasAsc(producto.getId(), canal.getId())) {
            map.put(p.getCuotas(), new BigDecimal[]{
                    p.getPvp(), p.getPvpInflado(), p.getCostoProducto(), p.getCostosVenta(),
                    p.getIngresoNetoVendedor(), p.getGanancia(), p.getMargenSobreIngresoNeto(),
                    p.getMargenSobrePvp(), p.getMarkupPorcentaje()
            });
        }
        return map;
    }

    private BigDecimal pvp(Integer cuotas) {
        return productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), cuotas)
                .map(ProductoCanalPrecio::getPvp)
                .orElse(null);
    }
}
