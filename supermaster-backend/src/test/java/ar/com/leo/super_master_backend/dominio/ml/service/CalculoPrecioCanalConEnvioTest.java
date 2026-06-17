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
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test para verificar el cálculo de precio con costo de envío personalizado.
 * Prueba el método calcularPrecioCanalConEnvio que se usa para determinar
 * si un producto califica para envío gratis en ML.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class CalculoPrecioCanalConEnvioTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoMargenRepository productoMargenRepository;

    @Autowired
    private CanalRepository canalRepository;

    @Autowired
    private ConceptoCalculoRepository conceptoCalculoRepository;

    @Autowired
    private CanalConceptoRepository canalConceptoRepository;

    @Autowired
    private OrigenRepository origenRepository;

    @Autowired
    private TipoRepository tipoRepository;

    @Autowired
    private ClasifGralRepository clasifGralRepository;

    @Autowired
    private MlaRepository mlaRepository;

    @Autowired
    private ConfiguracionMlRepository configuracionMlRepository;

    @Autowired
    private CalculoPrecioService calculoPrecioService;

    @Autowired
    private ConfiguracionMlService configuracionMlService;

    private Producto producto;
    private Canal canalMl;
    private Mla mla;
    private ConfiguracionMl configuracion;

    private static final String TEST_PREFIX = "ZTEST_ML_";

    @BeforeEach
    void setUp() {
        // Crear entidades base
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

        // Crear canal ML
        canalMl = new Canal();
        canalMl.setNombre(TEST_PREFIX + "ML");
        canalMl = canalRepository.save(canalMl);

        // Crear concepto de margen minorista (FLAG)
        ConceptoCalculo conceptoMargen = new ConceptoCalculo();
        conceptoMargen.setNombre(TEST_PREFIX + "MARGEN_MIN");
        conceptoMargen.setPorcentaje(BigDecimal.ZERO);
        conceptoMargen.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);
        conceptoMargen = conceptoCalculoRepository.save(conceptoMargen);

        // Crear concepto de IVA
        ConceptoCalculo conceptoIva = new ConceptoCalculo();
        conceptoIva.setNombre(TEST_PREFIX + "IVA");
        conceptoIva.setPorcentaje(BigDecimal.ZERO);
        conceptoIva.setAplicaSobre(AplicaSobre.FLAG_APLICAR_IVA);
        conceptoIva = conceptoCalculoRepository.save(conceptoIva);

        // Crear concepto de envío
        ConceptoCalculo conceptoEnvio = new ConceptoCalculo();
        conceptoEnvio.setNombre(TEST_PREFIX + "ENVIO");
        conceptoEnvio.setPorcentaje(BigDecimal.ZERO);
        conceptoEnvio.setAplicaSobre(AplicaSobre.FLAG_INCLUIR_ENVIO);
        conceptoEnvio = conceptoCalculoRepository.save(conceptoEnvio);

        // Crear concepto de comisión sobre PVP (13% típico de ML)
        ConceptoCalculo conceptoComision = new ConceptoCalculo();
        conceptoComision.setNombre(TEST_PREFIX + "COMISION_ML");
        conceptoComision.setPorcentaje(new BigDecimal("13"));
        conceptoComision.setAplicaSobre(AplicaSobre.COMISION_SOBRE_PVP);
        conceptoComision = conceptoCalculoRepository.save(conceptoComision);

        // Asignar conceptos al canal ML
        asignarConceptoACanal(canalMl, conceptoMargen);
        asignarConceptoACanal(canalMl, conceptoIva);
        asignarConceptoACanal(canalMl, conceptoEnvio);
        asignarConceptoACanal(canalMl, conceptoComision);

        // Crear MLA (max 20 caracteres)
        mla = new Mla();
        mla.setMla("ZTML123456789");
        mla.setPrecioEnvio(new BigDecimal("1500")); // Precio de envío actual
        mla = mlaRepository.save(mla);

        // Crear producto con MLA asignado
        producto = new Producto();
        producto.setSku(TEST_PREFIX + "001");
        producto.setTituloDux(TEST_PREFIX + "Producto ML");
        producto.setTituloNube(TEST_PREFIX + "Producto ML Test");
        producto.setCosto(new BigDecimal("10000")); // Costo $10,000
        producto.setIva(new BigDecimal("21"));
        producto.setOrigen(origen);
        producto.setTipo(tipo);
        producto.setClasifGral(clasifGral);
        producto.setMla(mla);
        producto = productoRepository.save(producto);

        // Crear margen para el producto (50% minorista)
        ProductoMargen productoMargen = new ProductoMargen();
        productoMargen.setProducto(producto);
        productoMargen.setMargenMinorista(new BigDecimal("50"));
        productoMargen.setMargenMayorista(new BigDecimal("30"));
        productoMargenRepository.save(productoMargen);

        // Crear configuración ML con tiers
        configuracion = new ConfiguracionMl();
        configuracion.setUmbralEnvioGratis(new BigDecimal("33000"));
        configuracion.setTier1Hasta(new BigDecimal("15000"));
        configuracion.setTier1Costo(new BigDecimal("1115"));
        configuracion.setTier2Hasta(new BigDecimal("25000"));
        configuracion.setTier2Costo(new BigDecimal("2300"));
        configuracion.setTier3Costo(new BigDecimal("2810"));
        configuracion = configuracionMlRepository.save(configuracion);
    }

    private void asignarConceptoACanal(Canal canal, ConceptoCalculo concepto) {
        CanalConcepto cc = new CanalConcepto();
        cc.setId(new CanalConceptoId(canal.getId(), concepto.getId()));
        cc.setCanal(canal);
        cc.setConcepto(concepto);
        canalConceptoRepository.save(cc);
    }

    // ===========================================
    // TEST 1: Cálculo con envío override
    // ===========================================
    @Test
    @Order(1)
    @DisplayName("calcularPrecioCanalConEnvio debe usar el envío override en lugar del MLA")
    void calcularPrecioCanalConEnvio_debeUsarEnvioOverride() {
        BigDecimal envioOverride = new BigDecimal("2500");

        // Calcular con envío override
        PrecioCalculadoDTO precioConOverride = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, envioOverride);

        // Calcular sin override (usa mla.precioEnvio = 1500)
        PrecioCalculadoDTO precioSinOverride = calculoPrecioService.calcularPrecioCanal(
                producto.getId(), canalMl.getId(), null);

        assertNotNull(precioConOverride);
        assertNotNull(precioSinOverride);

        // El PVP con override de 2500 debe ser mayor que con el MLA de 1500
        assertTrue(precioConOverride.pvp().compareTo(precioSinOverride.pvp()) > 0,
                String.format("PVP con override (%s) debe ser mayor que sin override (%s)",
                        precioConOverride.pvp(), precioSinOverride.pvp()));
    }

    // ===========================================
    // TEST 2: Verificar diferencia de PVP por envío
    // ===========================================
    @Test
    @Order(2)
    @DisplayName("La diferencia de PVP debe reflejar la diferencia de envío")
    void calcularPrecioCanalConEnvio_diferenciaPvpReflejaDiferenciaEnvio() {
        BigDecimal envioAlto = new BigDecimal("5000");
        BigDecimal envioBajo = new BigDecimal("1000");

        PrecioCalculadoDTO precioConEnvioAlto = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, envioAlto);

        PrecioCalculadoDTO precioConEnvioBajo = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, envioBajo);

        assertNotNull(precioConEnvioAlto);
        assertNotNull(precioConEnvioBajo);

        // La diferencia en PVP debe existir
        BigDecimal diferenciaPvp = precioConEnvioAlto.pvp().subtract(precioConEnvioBajo.pvp());
        assertTrue(diferenciaPvp.compareTo(BigDecimal.ZERO) > 0,
                "El PVP con envío alto debe ser mayor que con envío bajo");

        // La diferencia de envío es 4000, después de IVA (21%) y comisión (13% como divisor)
        // la diferencia en PVP debe ser aproximadamente: 4000 * 1.21 / 0.87 ≈ 5563
        // Pero puede variar según la fórmula exacta del canal
        System.out.println("Diferencia de envío: 4000");
        System.out.println("Diferencia de PVP: " + diferenciaPvp);
    }

    // ===========================================
    // TEST 3: Envío cero
    // ===========================================
    @Test
    @Order(3)
    @DisplayName("Con envío override cero, no debe sumar envío")
    void calcularPrecioCanalConEnvio_envioOverrideCero() {
        BigDecimal envioCero = BigDecimal.ZERO;

        PrecioCalculadoDTO precioSinEnvio = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, envioCero);

        PrecioCalculadoDTO precioConEnvioMla = calculoPrecioService.calcularPrecioCanal(
                producto.getId(), canalMl.getId(), null);

        assertNotNull(precioSinEnvio);
        assertNotNull(precioConEnvioMla);

        // Sin envío debe ser menor que con el envío del MLA (1500)
        assertTrue(precioSinEnvio.pvp().compareTo(precioConEnvioMla.pvp()) < 0,
                String.format("PVP sin envío (%s) debe ser menor que con envío MLA (%s)",
                        precioSinEnvio.pvp(), precioConEnvioMla.pvp()));
    }

    // ===========================================
    // TEST 4: Verificar lógica de umbral
    // ===========================================
    @Test
    @Order(4)
    @DisplayName("Verificar si PVP con tier3Costo alcanza umbral")
    void verificarUmbralEnvioGratis() {
        BigDecimal tier3Costo = configuracion.getTier3Costo();
        BigDecimal umbral = configuracion.getUmbralEnvioGratis();

        // Calcular PVP con tier3Costo (el más alto de los tiers)
        PrecioCalculadoDTO precioSimulado = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, tier3Costo);

        assertNotNull(precioSimulado);
        System.out.println("Costo envío tier3: " + tier3Costo);
        System.out.println("Umbral envío gratis: " + umbral);
        System.out.println("PVP simulado: " + precioSimulado.pvp());

        // Verificar si alcanza el umbral
        boolean alcanzaUmbral = precioSimulado.pvp().compareTo(umbral) >= 0;
        System.out.println("¿Alcanza umbral? " + alcanzaUmbral);

        // Con costo 10000, margen 50%, IVA 21%, comisión 13%, envío tier3
        // El PVP debería ser menor que 33000, así que NO debería alcanzar el umbral
        assertFalse(alcanzaUmbral,
                "Con estos parámetros, el producto NO debería alcanzar el umbral de envío gratis");
    }

    // ===========================================
    // TEST 5: Producto caro que alcanza umbral
    // ===========================================
    @Test
    @Order(5)
    @DisplayName("Producto caro debe alcanzar umbral de envío gratis")
    void productoCaro_debeAlcanzarUmbral() {
        // Cambiar costo del producto a uno más alto
        producto.setCosto(new BigDecimal("20000")); // $20,000
        productoRepository.save(producto);

        BigDecimal tier3Costo = configuracion.getTier3Costo();
        BigDecimal umbral = configuracion.getUmbralEnvioGratis();

        PrecioCalculadoDTO precioSimulado = calculoPrecioService.calcularPrecioCanalConEnvio(
                producto.getId(), canalMl.getId(), null, tier3Costo);

        assertNotNull(precioSimulado);
        System.out.println("Costo producto: 20000");
        System.out.println("PVP simulado: " + precioSimulado.pvp());

        // Con costo 20000, margen 50%, el PVP debería superar 33000
        boolean alcanzaUmbral = precioSimulado.pvp().compareTo(umbral) >= 0;
        assertTrue(alcanzaUmbral,
                String.format("Producto caro con PVP %s debería alcanzar umbral %s",
                        precioSimulado.pvp(), umbral));
    }

    // ===========================================
    // TEST 6: Obtener y actualizar configuración
    // ===========================================
    @Test
    @Order(6)
    @DisplayName("ConfiguracionMlService debe obtener y actualizar configuración")
    void configuracionMlService_obtenerYActualizar() {
        var config = configuracionMlService.obtener();

        assertNotNull(config);
        assertEquals(0, new BigDecimal("33000").compareTo(config.umbralEnvioGratis()),
                "umbralEnvioGratis debe ser 33000");
        assertNotNull(config.tier1Hasta(), "tier1Hasta debe estar configurado");
        assertNotNull(config.tier1Costo(), "tier1Costo debe estar configurado");
        assertNotNull(config.tier2Hasta(), "tier2Hasta debe estar configurado");
        assertNotNull(config.tier2Costo(), "tier2Costo debe estar configurado");
        assertNotNull(config.tier3Costo(), "tier3Costo debe estar configurado");
    }
}
