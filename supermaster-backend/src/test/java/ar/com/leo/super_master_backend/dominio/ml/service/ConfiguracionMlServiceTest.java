package ar.com.leo.super_master_backend.dominio.ml.service;

import ar.com.leo.super_master_backend.apis.ml.entity.ConfiguracionMl;
import ar.com.leo.super_master_backend.apis.ml.repository.ConfiguracionMlRepository;
import ar.com.leo.super_master_backend.apis.ml.service.ConfiguracionMlService;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoId;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.PrecioCalculadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.CalculoPrecioService;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test para verificar los límites de los tiers de costo de envío.
 *
 * Configuración de tiers:
 * - PVP < $15,000 → tier1Costo ($1,115)
 * - $15,000 <= PVP < $25,000 → tier2Costo ($2,300)
 * - $25,000 <= PVP < $33,000 → tier3Costo ($2,810)
 * - PVP >= $33,000 → null (usar API ML)
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class ConfiguracionMlServiceTest {

    @Autowired
    private ConfiguracionMlRepository repository;

    @Autowired
    private ConfiguracionMlService service;

    @Autowired
    private CalculoPrecioService calculoPrecioService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoMargenRepository productoMargenRepository;

    @Autowired
    private CanalRepository canalRepository;

    @Autowired
    private CanalConceptoRepository canalConceptoRepository;

    @Autowired
    private ConceptoCalculoRepository conceptoCalculoRepository;

    @Autowired
    private OrigenRepository origenRepository;

    @Autowired
    private TipoRepository tipoRepository;

    @Autowired
    private ClasifGralRepository clasifGralRepository;

    private Canal canalMl;
    private Producto producto;
    private static final String TEST_PREFIX = "ZTEST_TIER_";

    private static final BigDecimal TIER1_HASTA = new BigDecimal("15000");
    private static final BigDecimal TIER1_COSTO = new BigDecimal("1115");
    private static final BigDecimal TIER2_HASTA = new BigDecimal("25000");
    private static final BigDecimal TIER2_COSTO = new BigDecimal("2300");
    private static final BigDecimal TIER3_COSTO = new BigDecimal("2810");
    private static final BigDecimal UMBRAL = new BigDecimal("33000");

    @BeforeEach
    void setUp() {
        // El test corre contra la BD real (ver application-test.properties). El service
        // hace findFirstByOrderByIdAsc(), así que si ya hay una configuración persistida
        // con tiers distintos, se usa esa y nuestra fixture queda ignorada. Limpiamos
        // la tabla primero — el @Transactional del test rollbackea todo al finalizar.
        repository.deleteAllInBatch();
        repository.flush();

        // Crear configuración con tiers
        ConfiguracionMl config = new ConfiguracionMl();
        config.setUmbralEnvioGratis(UMBRAL);
        config.setTier1Hasta(TIER1_HASTA);
        config.setTier1Costo(TIER1_COSTO);
        config.setTier2Hasta(TIER2_HASTA);
        config.setTier2Costo(TIER2_COSTO);
        config.setTier3Costo(TIER3_COSTO);
        repository.save(config);

        // Crear entidades base para tests de simulación
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

        // Crear canal ML con conceptos
        canalMl = new Canal();
        canalMl.setNombre(TEST_PREFIX + "ML");
        canalMl = canalRepository.save(canalMl);

        // Concepto margen minorista
        ConceptoCalculo conceptoMargen = new ConceptoCalculo();
        conceptoMargen.setNombre(TEST_PREFIX + "MARGEN_MIN");
        conceptoMargen.setPorcentaje(BigDecimal.ZERO);
        conceptoMargen.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);
        conceptoMargen = conceptoCalculoRepository.save(conceptoMargen);
        asignarConceptoACanal(canalMl, conceptoMargen);

        // Concepto IVA
        ConceptoCalculo conceptoIva = new ConceptoCalculo();
        conceptoIva.setNombre(TEST_PREFIX + "IVA");
        conceptoIva.setPorcentaje(BigDecimal.ZERO);
        conceptoIva.setAplicaSobre(AplicaSobre.FLAG_APLICAR_IVA);
        conceptoIva = conceptoCalculoRepository.save(conceptoIva);
        asignarConceptoACanal(canalMl, conceptoIva);

        // Concepto envío
        ConceptoCalculo conceptoEnvio = new ConceptoCalculo();
        conceptoEnvio.setNombre(TEST_PREFIX + "ENVIO");
        conceptoEnvio.setPorcentaje(BigDecimal.ZERO);
        conceptoEnvio.setAplicaSobre(AplicaSobre.FLAG_INCLUIR_ENVIO);
        conceptoEnvio = conceptoCalculoRepository.save(conceptoEnvio);
        asignarConceptoACanal(canalMl, conceptoEnvio);

        // Crear producto base (se modificará el costo en cada test)
        producto = new Producto();
        producto.setSku(TEST_PREFIX + "001");
        producto.setDescripcion(TEST_PREFIX + "Producto Test");
        producto.setTituloWeb(TEST_PREFIX + "Producto Test");
        producto.setCosto(new BigDecimal("5000")); // Valor inicial
        producto.setIva(new BigDecimal("21"));
        producto.setOrigen(origen);
        producto.setTipo(tipo);
        producto.setClasifGral(clasifGral);
        producto = productoRepository.save(producto);

        // Crear margen (50% minorista)
        ProductoMargen margen = new ProductoMargen();
        margen.setProducto(producto);
        margen.setMargenMinorista(new BigDecimal("50"));
        margen.setMargenMayorista(new BigDecimal("30"));
        productoMargenRepository.save(margen);
    }

    private void asignarConceptoACanal(Canal canal, ConceptoCalculo concepto) {
        CanalConcepto cc = new CanalConcepto();
        cc.setId(new CanalConceptoId(canal.getId(), concepto.getId()));
        cc.setCanal(canal);
        cc.setConcepto(concepto);
        canalConceptoRepository.save(cc);
    }

    // =====================================================
    // TIER 1: PVP < $15,000
    // =====================================================

    @Test
    @Order(1)
    @DisplayName("PVP $14,999.99 → debe usar tier1Costo ($1,115)")
    void pvpJustoDebajoTier1_debeUsarTier1() {
        BigDecimal pvp = new BigDecimal("14999.99");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER1_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier1Costo ($%s), pero obtuvo $%s",
                        pvp, TIER1_COSTO, resultado));
    }

    @Test
    @Order(2)
    @DisplayName("PVP $15,000.00 → debe usar tier2Costo ($2,300) - límite EXCLUSIVO para tier1")
    void pvpExactoTier1Hasta_debeUsarTier2() {
        BigDecimal pvp = new BigDecimal("15000.00");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER2_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier2Costo ($%s), pero obtuvo $%s. " +
                        "El límite tier1Hasta es EXCLUSIVO.", pvp, TIER2_COSTO, resultado));
    }

    // =====================================================
    // TIER 2: $15,000 <= PVP < $25,000
    // =====================================================

    @Test
    @Order(3)
    @DisplayName("PVP $15,000.01 → debe usar tier2Costo ($2,300)")
    void pvpJustoArribaTier1_debeUsarTier2() {
        BigDecimal pvp = new BigDecimal("15000.01");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER2_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier2Costo ($%s), pero obtuvo $%s",
                        pvp, TIER2_COSTO, resultado));
    }

    @Test
    @Order(4)
    @DisplayName("PVP $24,999.99 → debe usar tier2Costo ($2,300)")
    void pvpJustoDebajoTier2_debeUsarTier2() {
        BigDecimal pvp = new BigDecimal("24999.99");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER2_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier2Costo ($%s), pero obtuvo $%s",
                        pvp, TIER2_COSTO, resultado));
    }

    @Test
    @Order(5)
    @DisplayName("PVP $25,000.00 → debe usar tier3Costo ($2,810) - límite EXCLUSIVO para tier2")
    void pvpExactoTier2Hasta_debeUsarTier3() {
        BigDecimal pvp = new BigDecimal("25000.00");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER3_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier3Costo ($%s), pero obtuvo $%s. " +
                        "El límite tier2Hasta es EXCLUSIVO.", pvp, TIER3_COSTO, resultado));
    }

    // =====================================================
    // TIER 3: $25,000 <= PVP < $33,000
    // =====================================================

    @Test
    @Order(6)
    @DisplayName("PVP $25,000.01 → debe usar tier3Costo ($2,810)")
    void pvpJustoArribaTier2_debeUsarTier3() {
        BigDecimal pvp = new BigDecimal("25000.01");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER3_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier3Costo ($%s), pero obtuvo $%s",
                        pvp, TIER3_COSTO, resultado));
    }

    @Test
    @Order(7)
    @DisplayName("PVP $32,999.99 → debe usar tier3Costo ($2,810)")
    void pvpJustoDebajoUmbral_debeUsarTier3() {
        BigDecimal pvp = new BigDecimal("32999.99");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNotNull(resultado);
        assertEquals(0, TIER3_COSTO.compareTo(resultado),
                String.format("PVP $%s debe usar tier3Costo ($%s), pero obtuvo $%s",
                        pvp, TIER3_COSTO, resultado));
    }

    // =====================================================
    // API ML: PVP >= $33,000
    // =====================================================

    @Test
    @Order(8)
    @DisplayName("PVP $33,000.00 → debe retornar null (usar API ML) - límite INCLUSIVO para API")
    void pvpExactoUmbral_debeUsarApiMl() {
        BigDecimal pvp = new BigDecimal("33000.00");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNull(resultado,
                String.format("PVP $%s debe retornar null (usar API ML), pero obtuvo $%s. " +
                        "El umbralEnvioGratis es INCLUSIVO para API ML.", pvp, resultado));
    }

    @Test
    @Order(9)
    @DisplayName("PVP $33,000.01 → debe retornar null (usar API ML)")
    void pvpArribaUmbral_debeUsarApiMl() {
        BigDecimal pvp = new BigDecimal("33000.01");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNull(resultado,
                String.format("PVP $%s debe retornar null (usar API ML), pero obtuvo $%s",
                        pvp, resultado));
    }

    @Test
    @Order(10)
    @DisplayName("PVP $50,000.00 → debe retornar null (usar API ML)")
    void pvpMuyAlto_debeUsarApiMl() {
        BigDecimal pvp = new BigDecimal("50000.00");
        BigDecimal resultado = service.obtenerCostoEnvioPorPvp(pvp);

        assertNull(resultado,
                String.format("PVP $%s debe retornar null (usar API ML), pero obtuvo $%s",
                        pvp, resultado));
    }

    // =====================================================
    // RESUMEN DE LÍMITES
    // =====================================================

    @Test
    @Order(11)
    @DisplayName("Resumen: verificar todos los límites de tiers")
    void resumenLimites() {
        System.out.println("\n=== RESUMEN DE LÍMITES DE TIERS ===\n");
        System.out.println("Configuración:");
        System.out.println("  tier1Hasta: $" + TIER1_HASTA + " (EXCLUSIVO)");
        System.out.println("  tier2Hasta: $" + TIER2_HASTA + " (EXCLUSIVO)");
        System.out.println("  umbralEnvioGratis: $" + UMBRAL + " (INCLUSIVO para API)\n");

        // Tabla de resultados
        BigDecimal[] pvps = {
                new BigDecimal("0"),
                new BigDecimal("14999.99"),
                new BigDecimal("15000.00"),
                new BigDecimal("24999.99"),
                new BigDecimal("25000.00"),
                new BigDecimal("32999.99"),
                new BigDecimal("33000.00"),
                new BigDecimal("50000.00")
        };

        System.out.println("| PVP          | Costo Envío  | Tier     |");
        System.out.println("|--------------|--------------|----------|");

        for (BigDecimal pvp : pvps) {
            BigDecimal costo = service.obtenerCostoEnvioPorPvp(pvp);
            String tier = determinarTier(pvp);
            String costoStr = costo != null ? "$" + costo : "API ML";
            System.out.printf("| $%-11s | %-12s | %-8s |%n", pvp, costoStr, tier);
        }

        System.out.println("\nLógica:");
        System.out.println("  - PVP < $15,000 → tier1Costo ($1,115)");
        System.out.println("  - $15,000 <= PVP < $25,000 → tier2Costo ($2,300)");
        System.out.println("  - $25,000 <= PVP < $33,000 → tier3Costo ($2,810)");
        System.out.println("  - PVP >= $33,000 → null (API ML)\n");
    }

    private String determinarTier(BigDecimal pvp) {
        if (pvp.compareTo(TIER1_HASTA) < 0) return "Tier 1";
        if (pvp.compareTo(TIER2_HASTA) < 0) return "Tier 2";
        if (pvp.compareTo(UMBRAL) < 0) return "Tier 3";
        return "API ML";
    }

    // =====================================================
    // SIMULACIÓN COMPLETA CON calcularPrecioCanalConEnvio
    // =====================================================

    @Test
    @Order(12)
    @DisplayName("Simulación: Producto con PVP en Tier 1 (< $15,000)")
    void simulacion_productoEnTier1() {
        // Producto con costo bajo que da PVP < $15,000
        producto.setCosto(new BigDecimal("5000"));
        productoRepository.save(producto);

        // Calcular PVP sin envío
        PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, BigDecimal.ZERO);

        BigDecimal pvpBase = precioSinEnvio.pvp();
        BigDecimal costoEnvioEsperado = service.obtenerCostoEnvioPorPvp(pvpBase);

        // Calcular PVP con el envío del tier
        PrecioCalculadoDTO precioConEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, costoEnvioEsperado);

        System.out.println("\n=== SIMULACIÓN TIER 1 ===");
        System.out.println("Costo producto: $" + producto.getCosto());
        System.out.println("PVP sin envío: $" + pvpBase);
        System.out.println("Tier aplicado: " + determinarTier(pvpBase));
        System.out.println("Costo envío (con IVA): $" + costoEnvioEsperado);
        System.out.println("Costo envío (sin IVA): $" + costoEnvioEsperado.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP));
        System.out.println("PVP final con envío: $" + precioConEnvio.pvp());

        assertTrue(pvpBase.compareTo(TIER1_HASTA) < 0,
                String.format("PVP base $%s debe ser < $%s para estar en Tier 1", pvpBase, TIER1_HASTA));
        assertEquals(0, TIER1_COSTO.compareTo(costoEnvioEsperado),
                String.format("Debe usar tier1Costo ($%s), pero obtuvo $%s", TIER1_COSTO, costoEnvioEsperado));
    }

    @Test
    @Order(13)
    @DisplayName("Simulación: Producto con PVP en Tier 2 ($15,000 - $25,000)")
    void simulacion_productoEnTier2() {
        // Producto con costo que da PVP entre $15,000 y $25,000
        producto.setCosto(new BigDecimal("10000"));
        productoRepository.save(producto);

        PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, BigDecimal.ZERO);

        BigDecimal pvpBase = precioSinEnvio.pvp();
        BigDecimal costoEnvioEsperado = service.obtenerCostoEnvioPorPvp(pvpBase);

        PrecioCalculadoDTO precioConEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, costoEnvioEsperado);

        System.out.println("\n=== SIMULACIÓN TIER 2 ===");
        System.out.println("Costo producto: $" + producto.getCosto());
        System.out.println("PVP sin envío: $" + pvpBase);
        System.out.println("Tier aplicado: " + determinarTier(pvpBase));
        System.out.println("Costo envío (con IVA): $" + costoEnvioEsperado);
        System.out.println("Costo envío (sin IVA): $" + costoEnvioEsperado.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP));
        System.out.println("PVP final con envío: $" + precioConEnvio.pvp());

        assertTrue(pvpBase.compareTo(TIER1_HASTA) >= 0 && pvpBase.compareTo(TIER2_HASTA) < 0,
                String.format("PVP base $%s debe estar entre $%s y $%s para Tier 2", pvpBase, TIER1_HASTA, TIER2_HASTA));
        assertEquals(0, TIER2_COSTO.compareTo(costoEnvioEsperado),
                String.format("Debe usar tier2Costo ($%s), pero obtuvo $%s", TIER2_COSTO, costoEnvioEsperado));
    }

    @Test
    @Order(14)
    @DisplayName("Simulación: Producto con PVP en Tier 3 ($25,000 - $33,000)")
    void simulacion_productoEnTier3() {
        // Producto con costo que da PVP entre $25,000 y $33,000
        producto.setCosto(new BigDecimal("15000"));
        productoRepository.save(producto);

        PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, BigDecimal.ZERO);

        BigDecimal pvpBase = precioSinEnvio.pvp();
        BigDecimal costoEnvioEsperado = service.obtenerCostoEnvioPorPvp(pvpBase);

        PrecioCalculadoDTO precioConEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, costoEnvioEsperado);

        System.out.println("\n=== SIMULACIÓN TIER 3 ===");
        System.out.println("Costo producto: $" + producto.getCosto());
        System.out.println("PVP sin envío: $" + pvpBase);
        System.out.println("Tier aplicado: " + determinarTier(pvpBase));
        System.out.println("Costo envío (con IVA): $" + costoEnvioEsperado);
        System.out.println("Costo envío (sin IVA): $" + costoEnvioEsperado.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP));
        System.out.println("PVP final con envío: $" + precioConEnvio.pvp());

        assertTrue(pvpBase.compareTo(TIER2_HASTA) >= 0 && pvpBase.compareTo(UMBRAL) < 0,
                String.format("PVP base $%s debe estar entre $%s y $%s para Tier 3", pvpBase, TIER2_HASTA, UMBRAL));
        assertEquals(0, TIER3_COSTO.compareTo(costoEnvioEsperado),
                String.format("Debe usar tier3Costo ($%s), pero obtuvo $%s", TIER3_COSTO, costoEnvioEsperado));
    }

    @Test
    @Order(15)
    @DisplayName("Simulación: Producto con PVP >= umbral (API ML)")
    void simulacion_productoEnUmbralApiMl() {
        // Producto con costo alto que da PVP >= $33,000
        producto.setCosto(new BigDecimal("20000"));
        productoRepository.save(producto);

        PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, BigDecimal.ZERO);

        BigDecimal pvpBase = precioSinEnvio.pvp();
        BigDecimal costoEnvio = service.obtenerCostoEnvioPorPvp(pvpBase);

        System.out.println("\n=== SIMULACIÓN API ML ===");
        System.out.println("Costo producto: $" + producto.getCosto());
        System.out.println("PVP sin envío: $" + pvpBase);
        System.out.println("Tier aplicado: " + determinarTier(pvpBase));
        System.out.println("Costo envío: " + (costoEnvio == null ? "null (usar API ML)" : "$" + costoEnvio));

        assertTrue(pvpBase.compareTo(UMBRAL) >= 0,
                String.format("PVP base $%s debe ser >= $%s para usar API ML", pvpBase, UMBRAL));
        assertNull(costoEnvio, "Debe retornar null para usar API ML");
    }

    @Test
    @Order(16)
    @DisplayName("Simulación completa: Tabla de productos por tier")
    void simulacion_tablaCompleta() {
        System.out.println("\n=== SIMULACIÓN COMPLETA: TABLA DE PRODUCTOS POR TIER ===\n");

        BigDecimal[] costos = {
                new BigDecimal("3000"),   // Debería caer en Tier 1
                new BigDecimal("5000"),   // Debería caer en Tier 1
                new BigDecimal("8000"),   // Debería caer en Tier 2
                new BigDecimal("10000"),  // Debería caer en Tier 2
                new BigDecimal("12000"),  // Debería caer en Tier 2 o Tier 3
                new BigDecimal("15000"),  // Debería caer en Tier 3
                new BigDecimal("18000"),  // Debería caer en Tier 3
                new BigDecimal("20000"),  // Debería caer en API ML
                new BigDecimal("25000"),  // Debería caer en API ML
        };

        System.out.println("| Costo      | PVP sin envío | Tier     | Costo Envío  | Costo s/IVA  | PVP Final    |");
        System.out.println("|------------|---------------|----------|--------------|--------------|--------------|");

        for (BigDecimal costo : costos) {
            producto.setCosto(costo);
            productoRepository.save(producto);

            PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                    producto.getId(), canalMl.getId(), null, BigDecimal.ZERO);

            BigDecimal pvpBase = precioSinEnvio.pvp();
            BigDecimal costoEnvio = service.obtenerCostoEnvioPorPvp(pvpBase);
            String tier = determinarTier(pvpBase);

            String costoEnvioStr;
            String costoSinIvaStr;
            String pvpFinalStr;

            if (costoEnvio != null) {
                BigDecimal costoSinIva = costoEnvio.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP);
                PrecioCalculadoDTO precioConEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                        producto.getId(), canalMl.getId(), null, costoEnvio);
                costoEnvioStr = "$" + costoEnvio;
                costoSinIvaStr = "$" + costoSinIva;
                pvpFinalStr = "$" + precioConEnvio.pvp();
            } else {
                costoEnvioStr = "API ML";
                costoSinIvaStr = "API ML";
                pvpFinalStr = "(depende API)";
            }

            System.out.printf("| $%-9s | $%-12s | %-8s | %-12s | %-12s | %-12s |%n",
                    costo, pvpBase, tier, costoEnvioStr, costoSinIvaStr, pvpFinalStr);
        }

        System.out.println("\nConfiguración de tiers:");
        System.out.println("  Tier 1: PVP < $" + TIER1_HASTA + " → $" + TIER1_COSTO + " (sin IVA: $" + TIER1_COSTO.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP) + ")");
        System.out.println("  Tier 2: $" + TIER1_HASTA + " <= PVP < $" + TIER2_HASTA + " → $" + TIER2_COSTO + " (sin IVA: $" + TIER2_COSTO.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP) + ")");
        System.out.println("  Tier 3: $" + TIER2_HASTA + " <= PVP < $" + UMBRAL + " → $" + TIER3_COSTO + " (sin IVA: $" + TIER3_COSTO.divide(new BigDecimal("1.21"), 2, RoundingMode.HALF_UP) + ")");
        System.out.println("  API ML: PVP >= $" + UMBRAL + " → consultar API\n");
    }
}
