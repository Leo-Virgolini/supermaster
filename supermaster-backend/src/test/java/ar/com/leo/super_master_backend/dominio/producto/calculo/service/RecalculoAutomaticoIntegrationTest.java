package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.*;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoId;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.canal.service.*;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.repository.ClasifGastroRepository;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.service.ClasifGastroService;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gral.repository.ClasifGralRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.NaturalezaConcepto;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.service.ConceptoCalculoService;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.marca.repository.MarcaRepository;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.material.repository.MaterialRepository;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.origen.repository.OrigenRepository;
import ar.com.leo.super_master_backend.dominio.precio_inflado.dto.PrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.TipoPrecioInflado;
import ar.com.leo.super_master_backend.dominio.precio_inflado.repository.PrecioInfladoRepository;
import ar.com.leo.super_master_backend.dominio.precio_inflado.service.PrecioInfladoService;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoCreateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoCanalPrecioInfladoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoMargenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.producto.mla.dto.MlaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.mla.service.MlaService;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoCanalPrecioInfladoService;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoMargenService;
import ar.com.leo.super_master_backend.dominio.producto.service.ProductoService;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorUpdateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.proveedor.repository.ProveedorRepository;
import ar.com.leo.super_master_backend.dominio.proveedor.service.ProveedorService;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.repository.ReglaDescuentoRepository;
import ar.com.leo.super_master_backend.dominio.regla_descuento.service.ReglaDescuentoService;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración para verificar que los triggers de recálculo automático
 * funcionan correctamente cuando se modifican las entidades relacionadas.
 *
 * Tests incluidos (40 total):
 *
 * ENTIDADES PRINCIPALES:
 * 1-2.   Producto (costo, IVA)
 * 3.     ProductoMargen (margen minorista)
 * 14.    ProductoMargen (margen mayorista)
 * 23.    ProductoMargen (cambio de margen minorista)
 *
 * CONCEPTOS DE CÁLCULO:
 * 4.     ConceptoCalculo (cambio de porcentaje)
 * 5-6.   CanalConcepto (asignar/quitar)
 * 15.    AJUSTE_MARGEN_PUNTOS
 * 16.    AJUSTE_MARGEN_PROPORCIONAL
 * 17.    GASTO_POST_GANANCIA
 * 18.    IMPUESTO_EN_FACTOR_IMP
 * 19.    GASTO_POST_IMPUESTOS
 * 20.    COSTO_OCULTO_PVP
 * 27.    DESCUENTO_PORCENTUAL
 * 28.    INFLACION_DIVISOR_FINAL
 *
 * CUOTAS:
 * 7.     CanalConceptoCuota (modificar porcentaje)
 * 21.    CanalConceptoCuota (eliminar)
 * 25.    CanalConceptoCuota (crear nueva)
 *
 * CANAL:
 * 8.     Canal (cambio de canalBase)
 *
 * RELACIONES:
 * 9.     Proveedor (porcentaje financiación)
 * 22.    Producto-Proveedor (cambio de proveedor)
 * 10.    ClasifGastro (esMaquina)
 * 11.    MLA (precioEnvio)
 * 26.    MLA (comisionPorcentaje)
 *
 * REGLAS DE DESCUENTO:
 * 12.    ReglaDescuento (crear/modificar)
 * 13.    ReglaDescuento (eliminar)
 *
 * PRECIO INFLADO:
 * 29.    PrecioInflado (crear asignación)
 * 30.    PrecioInflado (actualizar asignación)
 * 31.    PrecioInflado (eliminar asignación)
 *
 * REGLAS DE CONCEPTO:
 * 32.    CanalConceptoRegla EXCLUIR (excluye concepto del cálculo)
 * 36.    CanalConceptoRegla INCLUIR (incluye concepto solo si cumple condiciones)
 *
 * ATRIBUTOS QUE AFECTAN REGLAS:
 * 33.    Producto-Tipo (cambio de tipo afecta reglas de concepto)
 * 34.    Producto-Marca (cambio de marca afecta reglas de concepto)
 * 35.    Producto-ClasifGral (cambio de clasifGral afecta reglas de concepto)
 *
 * FLAGS ML Y ENVÍO:
 * 37.    FLAG_INCLUIR_ENVIO (incluir costo de envío ML en el PVP)
 * 38.    FLAG_COMISION_ML (usar comisión de ML desde el MLA)
 * 39.    FLAG_COMISION_ML con naturaleza override INFLACION (inflar PVP sin contar como costo)
 *
 * PRECIO INFLADO (regla):
 * 40.    PrecioInflado (modificar valor de la regla recalcula asignaciones)
 *
 * MÚLTIPLES PRODUCTOS:
 * 24.    Recálculo en cascada de múltiples productos
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class RecalculoAutomaticoIntegrationTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoMargenRepository productoMargenRepository;

    @Autowired
    private CanalRepository canalRepository;

    @Autowired
    private ConceptoCalculoRepository conceptoGastoRepository;

    @Autowired
    private CanalConceptoRepository canalConceptoRepository;

    @Autowired
    private CanalConceptoCuotaRepository canalConceptoCuotaRepository;

    @Autowired
    private CanalConceptoCuotaService canalConceptoCuotaService;

    @Autowired
    private ProductoCanalPrecioRepository productoCanalPrecioRepository;

    @Autowired
    private OrigenRepository origenRepository;

    @Autowired
    private TipoRepository tipoRepository;

    @Autowired
    private ClasifGralRepository clasifGralRepository;

    @Autowired
    private ClasifGastroRepository clasifGastroRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Autowired
    private MlaRepository mlaRepository;

    @Autowired
    private ReglaDescuentoRepository reglaDescuentoRepository;

    @Autowired
    private CalculoPrecioService calculoPrecioService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoMargenService productoMargenService;

    @Autowired
    private ConceptoCalculoService conceptoGastoService;

    @Autowired
    private CanalConceptoService canalConceptoService;

    @Autowired
    private CanalService canalService;

    @Autowired
    private ProveedorService proveedorService;

    @Autowired
    private ClasifGastroService clasifGastroService;

    @Autowired
    private MlaService mlaService;

    @Autowired
    private ReglaDescuentoService reglaDescuentoService;

    @Autowired
    private PrecioInfladoRepository precioInfladoRepository;

    @Autowired
    private PrecioInfladoService precioInfladoService;

    @Autowired
    private ProductoCanalPrecioInfladoService productoCanalPrecioInfladoService;

    @Autowired
    private CanalConceptoReglaService canalConceptoReglaService;

    @Autowired
    private CanalReglaService canalReglaService;

    @Autowired
    private MarcaRepository marcaRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Entidades de prueba
    private Producto producto;
    private Canal canal;
    private ConceptoCalculo conceptoMargen;
    private ConceptoCalculo conceptoComision;
    private ProductoMargen productoMargen;

    private static final String TEST_PREFIX = "ZTEST_";

    @BeforeEach
    void setUp() {
        // Con @Transactional, cada test tiene su propia transacción que hace rollback al final
        // No necesitamos limpiar datos porque no se persisten

        // Buscar entidades base existentes o crearlas
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

        // Marca, proveedor y material base: requeridos por la validación
        // "producto simple completo" (un producto no combo debe tenerlos).
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

        // Crear canal
        canal = new Canal();
        canal.setNombre(TEST_PREFIX + "CANAL");
        canal = canalRepository.save(canal);

        // Crear concepto de margen minorista (FLAG)
        conceptoMargen = new ConceptoCalculo();
        conceptoMargen.setNombre(TEST_PREFIX + "MARGEN_MIN");
        conceptoMargen.setPorcentaje(BigDecimal.ZERO);
        conceptoMargen.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);
        conceptoMargen = conceptoGastoRepository.save(conceptoMargen);

        // Crear concepto de comisión sobre PVP
        conceptoComision = new ConceptoCalculo();
        conceptoComision.setNombre(TEST_PREFIX + "COMISION");
        conceptoComision.setPorcentaje(new BigDecimal("10"));
        conceptoComision.setAplicaSobre(AplicaSobre.COMISION_SOBRE_PVP);
        conceptoComision = conceptoGastoRepository.save(conceptoComision);

        // Asignar conceptos al canal
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

        // Crear producto
        producto = new Producto();
        producto.setSku(TEST_PREFIX + "001");
        producto.setTituloDux(TEST_PREFIX + "Producto de prueba");
        producto.setTituloNube(TEST_PREFIX + "Producto Test");
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

        // Crear margen para el producto
        productoMargen = new ProductoMargen();
        productoMargen.setProducto(producto);
        productoMargen.setMargenMinorista(new BigDecimal("50"));
        productoMargen.setMargenMayorista(new BigDecimal("30"));
        productoMargen = productoMargenRepository.save(productoMargen);

        // Crear cuota de contado (1 pago) para que el cálculo funcione
        CanalConceptoCuota cuotaContado = new CanalConceptoCuota();
        cuotaContado.setCanal(canal);
        cuotaContado.setCuotas(1); // 1 = contado/1 pago
        cuotaContado.setPorcentaje(BigDecimal.ZERO);
        cuotaContado.setDescripcion("Contado");
        canalConceptoCuotaRepository.save(cuotaContado);

        // Sincronizar con la BD antes de calcular
        entityManager.flush();

        // Calcular precio inicial
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // Sincronizar el precio calculado
        entityManager.flush();
    }

    // No necesitamos @AfterEach porque @Transactional hace rollback automáticamente

    private BigDecimal obtenerPvpActual() {
        entityManager.flush();
        entityManager.clear(); // Limpiar cache de primer nivel para ver datos frescos
        var precio = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1);
        assertTrue(precio.isPresent(), "Debe existir al menos un precio calculado");
        return precio.get().getPvp();
    }

    /**
     * Builds a full ProductoUpdateDTO from the entity's current state.
     * Simulates what the frontend does: sends ALL field values.
     * Override parameters (if non-null) replace the entity's current value.
     */
    private ProductoUpdateDTO buildProductoUpdate(BigDecimal costoOverride, BigDecimal ivaOverride,
            Integer marcaIdOverride, Integer tipoIdOverride, Integer clasifGralIdOverride, Integer proveedorIdOverride) {
        return new ProductoUpdateDTO(
                producto.getSku(), producto.getCodExt(), producto.getTituloDux(), producto.getTituloMl(), producto.getTituloNube(),
                producto.getEsCombo(), producto.getUxb(), producto.getMoq(), producto.getImagenUrl(),
                producto.getStock(), producto.getActivo(),
                marcaIdOverride != null ? marcaIdOverride : (producto.getMarca() != null ? producto.getMarca().getId() : null),
                producto.getOrigen() != null ? producto.getOrigen().getId() : null,
                clasifGralIdOverride != null ? clasifGralIdOverride : (producto.getClasifGral() != null ? producto.getClasifGral().getId() : null),
                producto.getClasifGastro() != null ? producto.getClasifGastro().getId() : null,
                tipoIdOverride != null ? tipoIdOverride : (producto.getTipo() != null ? producto.getTipo().getId() : null),
                proveedorIdOverride != null ? proveedorIdOverride : (producto.getProveedor() != null ? producto.getProveedor().getId() : null),
                producto.getMaterial() != null ? producto.getMaterial().getId() : null,
                producto.getMla() != null ? producto.getMla().getId() : null,
                producto.getCapacidad(), producto.getLargo(), producto.getAncho(), producto.getAlto(),
                producto.getDiamboca(), producto.getDiambase(), producto.getEspesor(),
                costoOverride != null ? costoOverride : producto.getCosto(),
                ivaOverride != null ? ivaOverride : producto.getIva(),
                producto.getTagReposicion(),
                producto.getTag()
        );
    }

    private BigDecimal obtenerPvpInfladoActual() {
        entityManager.flush();
        entityManager.clear();
        var precio = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1);
        assertTrue(precio.isPresent(), "Debe existir al menos un precio calculado");
        return precio.get().getPvpInflado();
    }

    // ===========================================
    // TEST 1: Cambio en Producto (costo)
    // ===========================================
    @Test
    @Order(1)
    @DisplayName("1. Recálculo automático al cambiar costo del Producto")
    void testRecalculoPorCambioCostoProducto() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar costo del producto usando el servicio
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(new BigDecimal("1500"), null, null, null, null, null));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el costo del producto");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar el costo");
    }

    // ===========================================
    // TEST 2: Cambio en Producto (IVA)
    // ===========================================
    @Test
    @Order(2)
    @DisplayName("2. Recálculo automático al cambiar IVA del Producto")
    void testRecalculoPorCambioIvaProducto() {
        // Primero necesitamos agregar el concepto FLAG_APLICAR_IVA
        ConceptoCalculo conceptoIva = new ConceptoCalculo();
        conceptoIva.setNombre(TEST_PREFIX + "IVA");
        conceptoIva.setPorcentaje(BigDecimal.ZERO);
        conceptoIva.setAplicaSobre(AplicaSobre.FLAG_APLICAR_IVA);
        conceptoIva = conceptoGastoRepository.save(conceptoIva);

        CanalConcepto ccIva = new CanalConcepto();
        ccIva.setId(new CanalConceptoId(canal.getId(), conceptoIva.getId()));
        ccIva.setCanal(canal);
        ccIva.setConcepto(conceptoIva);
        canalConceptoRepository.save(ccIva);

        // Recalcular con IVA
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar IVA del producto
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(null, new BigDecimal("10.5"), null, null, null, null));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el IVA del producto");
    }

    // ===========================================
    // TEST 3: Cambio en ProductoMargen
    // ===========================================
    @Test
    @Order(3)
    @DisplayName("3. Recálculo automático al cambiar ProductoMargen")
    void testRecalculoPorCambioProductoMargen() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar margen del producto
        productoMargenService.guardar(
                new ProductoMargenDTO(
                        productoMargen.getId(),
                        producto.getId(),
                        new BigDecimal("70"), // nuevo margen minorista
                        new BigDecimal("30"),
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el margen del producto");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar el margen");
    }

    // ===========================================
    // TEST 4: Cambio en ConceptoCalculo (porcentaje)
    // ===========================================
    @Test
    @Order(4)
    @DisplayName("4. Recálculo automático al cambiar porcentaje de ConceptoCalculo")
    void testRecalculoPorCambioConceptoCalculo() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar porcentaje del concepto de comisión
        conceptoGastoService.actualizar(conceptoComision.getId(),
                new ConceptoCalculoUpdateDTO(
                        conceptoComision.getNombre(),
                        new BigDecimal("20"), // aumentar comisión de 10% a 20%
                        conceptoComision.getAplicaSobre().name(),
                        null,
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el porcentaje del concepto");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar la comisión (divisor mayor)");
    }

    // ===========================================
    // TEST 5: Cambio en CanalConcepto (asignar concepto)
    // ===========================================
    @Test
    @Order(5)
    @DisplayName("5. Recálculo automático al asignar concepto a canal")
    void testRecalculoPorAsignarConceptoACanal() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear y asignar nuevo concepto de gasto sobre costo
        ConceptoCalculo conceptoEmbalaje = new ConceptoCalculo();
        conceptoEmbalaje.setNombre(TEST_PREFIX + "EMBALAJE");
        conceptoEmbalaje.setPorcentaje(new BigDecimal("5"));
        conceptoEmbalaje.setAplicaSobre(AplicaSobre.GASTO_SOBRE_COSTO);
        conceptoEmbalaje = conceptoGastoRepository.save(conceptoEmbalaje);

        // Asignar al canal (esto debe disparar recálculo)
        canalConceptoService.asignarConcepto(canal.getId(), conceptoEmbalaje.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al asignar un nuevo concepto al canal");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar gasto sobre costo");
    }

    // ===========================================
    // TEST 6: Cambio en CanalConcepto (quitar concepto)
    // ===========================================
    @Test
    @Order(6)
    @DisplayName("6. Recálculo automático al quitar concepto de canal")
    void testRecalculoPorQuitarConceptoDeCanal() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Quitar el concepto de comisión (esto debe disparar recálculo)
        canalConceptoService.eliminarConcepto(canal.getId(), conceptoComision.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al quitar un concepto del canal");
        assertTrue(pvpNuevo.compareTo(pvpInicial) < 0,
                "El PVP debe disminuir al quitar la comisión");
    }

    // ===========================================
    // TEST 7: Cambio en CanalConceptoCuota
    // ===========================================
    @Test
    @Order(7)
    @DisplayName("7. Recálculo automático al modificar cuotas del canal")
    void testRecalculoPorCambioCuotaCanal() {
        // Crear una cuota para el canal usando el servicio
        var cuotaCreada = canalConceptoCuotaService.crear(
                new CanalConceptoCuotaCreateDTO(canal.getId(), 3, new BigDecimal("15"), "3 cuotas"));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // Obtener precio para 3 cuotas
        entityManager.flush();
        entityManager.clear();

        var precioCuotas = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 3);

        assertTrue(precioCuotas.isPresent(), "Debe existir precio para 3 cuotas");
        BigDecimal pvpCuotasInicial = precioCuotas.get().getPvp();

        // Modificar el porcentaje de la cuota usando el servicio
        canalConceptoCuotaService.actualizar(cuotaCreada.id(),
                new CanalConceptoCuotaUpdateDTO(3, new BigDecimal("25"), "3 cuotas"));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        entityManager.flush();
        entityManager.clear();

        precioCuotas = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 3);

        assertTrue(precioCuotas.isPresent(), "Debe seguir existiendo precio para 3 cuotas");
        BigDecimal pvpCuotasNuevo = precioCuotas.get().getPvp();

        assertNotEquals(pvpCuotasInicial, pvpCuotasNuevo,
                "El PVP de cuotas debe cambiar al modificar el porcentaje");
    }

    // ===========================================
    // TEST 8: Cambio en Canal (canalBase)
    // ===========================================
    @Test
    @Order(8)
    @DisplayName("8. Recálculo automático al cambiar canalBase del canal")
    void testRecalculoPorCambioCanalBase() {
        // Crear canal padre 1
        Canal canalPadre1 = new Canal();
        canalPadre1.setNombre(TEST_PREFIX + "PADRE1");
        canalPadre1 = canalRepository.save(canalPadre1);

        // Asignar concepto de margen al canal padre 1
        CanalConcepto ccPadre1 = new CanalConcepto();
        ccPadre1.setId(new CanalConceptoId(canalPadre1.getId(), conceptoMargen.getId()));
        ccPadre1.setCanal(canalPadre1);
        ccPadre1.setConcepto(conceptoMargen);
        canalConceptoRepository.save(ccPadre1);

        // Agregar cuota al canal padre 1
        CanalConceptoCuota cuotaPadre1 = new CanalConceptoCuota();
        cuotaPadre1.setCanal(canalPadre1);
        cuotaPadre1.setCuotas(1);
        cuotaPadre1.setPorcentaje(BigDecimal.ZERO);
        cuotaPadre1.setDescripcion("Contado");
        canalConceptoCuotaRepository.save(cuotaPadre1);

        // Crear canal padre 2 con precio diferente (mayor comisión)
        Canal canalPadre2 = new Canal();
        canalPadre2.setNombre(TEST_PREFIX + "PADRE2");
        canalPadre2 = canalRepository.save(canalPadre2);

        CanalConcepto ccPadre2Margen = new CanalConcepto();
        ccPadre2Margen.setId(new CanalConceptoId(canalPadre2.getId(), conceptoMargen.getId()));
        ccPadre2Margen.setCanal(canalPadre2);
        ccPadre2Margen.setConcepto(conceptoMargen);
        canalConceptoRepository.save(ccPadre2Margen);

        // También asignar la comisión al padre 2 (mayor precio)
        CanalConcepto ccPadre2Comision = new CanalConcepto();
        ccPadre2Comision.setId(new CanalConceptoId(canalPadre2.getId(), conceptoComision.getId()));
        ccPadre2Comision.setCanal(canalPadre2);
        ccPadre2Comision.setConcepto(conceptoComision);
        canalConceptoRepository.save(ccPadre2Comision);

        CanalConceptoCuota cuotaPadre2 = new CanalConceptoCuota();
        cuotaPadre2.setCanal(canalPadre2);
        cuotaPadre2.setCuotas(1);
        cuotaPadre2.setPorcentaje(BigDecimal.ZERO);
        cuotaPadre2.setDescripcion("Contado");
        canalConceptoCuotaRepository.save(cuotaPadre2);

        entityManager.flush();

        // Calcular precio en ambos padres
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canalPadre1.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canalPadre2.getId());

        // Crear canal hijo
        Canal canalHijo = new Canal();
        canalHijo.setNombre(TEST_PREFIX + "HIJO");
        canalHijo = canalRepository.save(canalHijo);

        // Crear concepto CALCULO_SOBRE_CANAL_BASE
        ConceptoCalculo conceptoSobreBase = new ConceptoCalculo();
        conceptoSobreBase.setNombre(TEST_PREFIX + "SOBRE_BASE");
        conceptoSobreBase.setPorcentaje(new BigDecimal("-10")); // 10% menos que el padre
        conceptoSobreBase.setAplicaSobre(AplicaSobre.CALCULO_SOBRE_CANAL_BASE);
        conceptoSobreBase = conceptoGastoRepository.save(conceptoSobreBase);

        // Asignar concepto al canal hijo
        CanalConcepto ccHijoBase = new CanalConcepto();
        ccHijoBase.setId(new CanalConceptoId(canalHijo.getId(), conceptoSobreBase.getId()));
        ccHijoBase.setCanal(canalHijo);
        ccHijoBase.setConcepto(conceptoSobreBase);
        canalConceptoRepository.save(ccHijoBase);

        // Agregar cuota al canal hijo
        CanalConceptoCuota cuotaHijo = new CanalConceptoCuota();
        cuotaHijo.setCanal(canalHijo);
        cuotaHijo.setCuotas(1);
        cuotaHijo.setPorcentaje(BigDecimal.ZERO);
        cuotaHijo.setDescripcion("Contado");
        canalConceptoCuotaRepository.save(cuotaHijo);

        // Configurar canalBase del hijo apuntando al padre 1
        canalHijo.setCanalBase(canalPadre1);
        canalHijo = canalRepository.save(canalHijo);

        entityManager.flush();

        // Calcular precio del hijo (basado en padre 1)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canalHijo.getId());

        entityManager.flush();
        entityManager.clear();

        var precioHijoOpt = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canalHijo.getId(), 1);
        assertTrue(precioHijoOpt.isPresent(), "Debe existir precio en canal hijo");
        BigDecimal pvpHijoInicial = precioHijoOpt.get().getPvp();

        // Cambiar el canalBase al padre 2 (esto debe disparar recálculo)
        canalService.actualizar(canalHijo.getId(), new CanalUpdateDTO(TEST_PREFIX + "HIJO", canalPadre2.getId()));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canalHijo.getId());

        entityManager.flush();
        entityManager.clear();

        precioHijoOpt = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canalHijo.getId(), 1);

        assertTrue(precioHijoOpt.isPresent(), "Debe seguir existiendo precio en canal hijo");
        BigDecimal pvpHijoNuevo = precioHijoOpt.get().getPvp();
        assertNotEquals(pvpHijoInicial, pvpHijoNuevo,
                "El PVP debe cambiar al modificar el canalBase de padre1 a padre2");
    }

    // ===========================================
    // TEST 9: Cambio en Proveedor (porcentaje)
    // ===========================================
    @Test
    @Order(9)
    @DisplayName("9. Recálculo automático al cambiar porcentaje del Proveedor")
    void testRecalculoPorCambioProveedor() {
        // Crear proveedor
        Proveedor proveedor = new Proveedor();
        proveedor.setNombre(TEST_PREFIX + "Proveedor");
        proveedor.setApodo(TEST_PREFIX + "Prov");
        proveedor.setFinanciacionPorcentaje(new BigDecimal("5"));
        proveedor = proveedorRepository.save(proveedor);

        // Asignar proveedor al producto
        producto.setProveedor(proveedor);
        producto = productoRepository.save(producto);

        // Crear concepto FLAG_FINANCIACION_PROVEEDOR
        ConceptoCalculo conceptoFinanciacion = new ConceptoCalculo();
        conceptoFinanciacion.setNombre(TEST_PREFIX + "FIN_PROV");
        conceptoFinanciacion.setPorcentaje(BigDecimal.ZERO);
        conceptoFinanciacion.setAplicaSobre(AplicaSobre.FLAG_FINANCIACION_PROVEEDOR);
        conceptoFinanciacion = conceptoGastoRepository.save(conceptoFinanciacion);

        // Asignar al canal
        CanalConcepto ccFin = new CanalConcepto();
        ccFin.setId(new CanalConceptoId(canal.getId(), conceptoFinanciacion.getId()));
        ccFin.setCanal(canal);
        ccFin.setConcepto(conceptoFinanciacion);
        canalConceptoRepository.save(ccFin);

        // Recalcular
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar porcentaje del proveedor
        proveedorService.actualizar(proveedor.getId(),
                new ProveedorUpdateDTO(
                        TEST_PREFIX + "Proveedor", TEST_PREFIX + "Prov", null, null,
                        new BigDecimal("15"), // aumentar financiación
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el porcentaje del proveedor");
    }

    // ===========================================
    // TEST 10: Regla de concepto por tag = MAQUINA
    // ===========================================
    @Test
    @Order(10)
    @DisplayName("10. Una regla INCLUIR con tag=MAQUINA aplica el concepto solo si el producto es MAQUINA")
    void testReglaConceptoPorTagMaquina() {
        // Concepto de gasto que solo debe aplicar a máquinas
        ConceptoCalculo conceptoMaquina = new ConceptoCalculo();
        conceptoMaquina.setNombre(TEST_PREFIX + "GASTO_MAQUINA");
        conceptoMaquina.setPorcentaje(new BigDecimal("8"));
        conceptoMaquina.setAplicaSobre(AplicaSobre.GASTO_SOBRE_COSTO);
        conceptoMaquina = conceptoGastoRepository.save(conceptoMaquina);

        canalConceptoService.asignarConcepto(canal.getId(), conceptoMaquina.getId());

        // Regla INCLUIR: el concepto solo aplica si el producto tiene tag = MAQUINA
        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoMaquina.getId(),
                        "INCLUIR",
                        null, null, null, null,
                        Tag.MAQUINA, // tag = MAQUINA
                        null         // tieneEnvio
                ));

        // Producto NO máquina (tag MENAJE) → el concepto NO aplica (regla INCLUIR no cumplida)
        producto.setTag(Tag.MENAJE);
        producto = productoRepository.save(producto);
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpSinMaquina = obtenerPvpActual();

        // Producto máquina (tag MAQUINA) → el concepto SÍ aplica
        producto.setTag(Tag.MAQUINA);
        producto = productoRepository.save(producto);
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConMaquina = obtenerPvpActual();

        assertNotEquals(pvpSinMaquina, pvpConMaquina,
                "El PVP debe cambiar cuando el tag pasa de MENAJE a MAQUINA y hay una regla INCLUIR por tag");
        assertTrue(pvpConMaquina.compareTo(pvpSinMaquina) > 0,
                "El PVP debe aumentar porque el gasto sobre costo ahora aplica");
    }

    // ===========================================
    // TEST 11: Cambio en MLA (precioEnvio)
    // ===========================================
    @Test
    @Order(11)
    @DisplayName("11. Recálculo automático al cambiar precioEnvio del MLA")
    void testRecalculoPorCambioMla() {
        // Crear MLA
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA123");
        mla.setPrecioEnvio(new BigDecimal("500"));
        mla = mlaRepository.save(mla);

        // Asignar MLA al producto
        producto.setMla(mla);
        producto = productoRepository.save(producto);

        // Crear concepto FLAG_INCLUIR_ENVIO
        ConceptoCalculo conceptoEnvio = new ConceptoCalculo();
        conceptoEnvio.setNombre(TEST_PREFIX + "ENVIO");
        conceptoEnvio.setPorcentaje(BigDecimal.ZERO);
        conceptoEnvio.setAplicaSobre(AplicaSobre.FLAG_INCLUIR_ENVIO);
        conceptoEnvio = conceptoGastoRepository.save(conceptoEnvio);

        CanalConcepto ccEnvio = new CanalConcepto();
        ccEnvio.setId(new CanalConceptoId(canal.getId(), conceptoEnvio.getId()));
        ccEnvio.setCanal(canal);
        ccEnvio.setConcepto(conceptoEnvio);
        canalConceptoRepository.save(ccEnvio);

        // Recalcular con envío
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar precio de envío (mantener el código MLA original)
        mlaService.actualizar(mla.getId(),
                new MlaUpdateDTO(TEST_PREFIX + "MLA123", null, new BigDecimal("1000"), null, 0));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el precio de envío del MLA");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar el precio de envío");
    }

    // ===========================================
    // TEST 12: Cambio en ReglaDescuento (crear/modificar)
    // ===========================================
    @Test
    @Order(12)
    @DisplayName("12. Recálculo automático al crear/modificar ReglaDescuento")
    void testRecalculoPorCambioReglaDescuento() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear regla de descuento
        ReglaDescuentoCreateDTO reglaDto = new ReglaDescuentoCreateDTO(
                canal.getId(),
                null, null, null,
                BigDecimal.ZERO, // monto mínimo
                new BigDecimal("5"), // 5% descuento
                1, true, "Regla test"
        );

        var reglaCreada = reglaDescuentoService.crear(reglaDto);

        BigDecimal pvpDespuesCrear = obtenerPvpActual();

        // Las reglas de descuento son informativas: no afectan el PVP, solo se muestran
        assertEquals(pvpInicial, pvpDespuesCrear,
                "El PVP no debe cambiar al crear una regla de descuento (son informativas)");

        // Modificar la regla
        reglaDescuentoService.actualizar(reglaCreada.id(),
                new ReglaDescuentoUpdateDTO(
                        canal.getId(), null, null, null,
                        BigDecimal.ZERO,
                        new BigDecimal("10"), // aumentar descuento
                        1, true, "Regla test"
                ));

        BigDecimal pvpDespuesModificar = obtenerPvpActual();

        // El PVP se mantiene igual porque las reglas de descuento no afectan el cálculo
        assertEquals(pvpDespuesCrear, pvpDespuesModificar,
                "El PVP no debe cambiar al modificar la regla de descuento (son informativas)");
    }

    // ===========================================
    // TEST 13: Eliminar ReglaDescuento
    // ===========================================
    @Test
    @Order(13)
    @DisplayName("13. Recálculo automático al eliminar ReglaDescuento")
    void testRecalculoPorEliminarReglaDescuento() {
        // Crear regla de descuento
        ReglaDescuentoCreateDTO reglaDto = new ReglaDescuentoCreateDTO(
                canal.getId(),
                null, null, null,
                BigDecimal.ZERO,
                new BigDecimal("10"),
                1, true, "Regla a eliminar"
        );

        var reglaCreada = reglaDescuentoService.crear(reglaDto);
        BigDecimal pvpConRegla = obtenerPvpActual();

        // Eliminar la regla
        reglaDescuentoService.eliminar(reglaCreada.id());

        BigDecimal pvpSinRegla = obtenerPvpActual();

        // Las reglas de descuento son informativas: no afectan el PVP
        assertEquals(pvpConRegla, pvpSinRegla,
                "El PVP no debe cambiar al eliminar una regla de descuento (son informativas)");
    }

    // ===========================================
    // TEST 14: Cambio en ProductoMargen (margen mayorista)
    // ===========================================
    @Test
    @Order(14)
    @DisplayName("14. Recálculo automático al cambiar margen mayorista")
    void testRecalculoPorCambioMargenMayorista() {
        // Cambiar el canal para usar margen mayorista
        ConceptoCalculo conceptoMargenMay = new ConceptoCalculo();
        conceptoMargenMay.setNombre(TEST_PREFIX + "MARGEN_MAY");
        conceptoMargenMay.setPorcentaje(BigDecimal.ZERO);
        conceptoMargenMay.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MAYORISTA);
        conceptoMargenMay = conceptoGastoRepository.save(conceptoMargenMay);

        // Quitar margen minorista y agregar mayorista
        canalConceptoService.eliminarConcepto(canal.getId(), conceptoMargen.getId());
        canalConceptoService.asignarConcepto(canal.getId(), conceptoMargenMay.getId());

        // Recalcular
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar margen mayorista
        productoMargenService.guardar(
                new ProductoMargenDTO(
                        productoMargen.getId(),
                        producto.getId(),
                        new BigDecimal("50"),
                        new BigDecimal("50"), // aumentar margen mayorista de 30 a 50
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el margen mayorista");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar el margen mayorista");
    }

    // ===========================================
    // TEST 15: Concepto AJUSTE_MARGEN_PUNTOS
    // ===========================================
    @Test
    @Order(15)
    @DisplayName("15. Recálculo automático con concepto AJUSTE_MARGEN_PUNTOS")
    void testRecalculoConAjusteMargenPuntos() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto AJUSTE_MARGEN_PUNTOS
        ConceptoCalculo conceptoAjuste = new ConceptoCalculo();
        conceptoAjuste.setNombre(TEST_PREFIX + "AJUSTE_PUNTOS");
        conceptoAjuste.setPorcentaje(new BigDecimal("10")); // +10 puntos al margen
        conceptoAjuste.setAplicaSobre(AplicaSobre.AJUSTE_MARGEN_PUNTOS);
        conceptoAjuste = conceptoGastoRepository.save(conceptoAjuste);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoAjuste.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar AJUSTE_MARGEN_PUNTOS");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar puntos al margen");
    }

    // ===========================================
    // TEST 16: Concepto AJUSTE_MARGEN_PROPORCIONAL
    // ===========================================
    @Test
    @Order(16)
    @DisplayName("16. Recálculo automático con concepto AJUSTE_MARGEN_PROPORCIONAL")
    void testRecalculoConAjusteMargenProporcional() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto AJUSTE_MARGEN_PROPORCIONAL
        ConceptoCalculo conceptoAjuste = new ConceptoCalculo();
        conceptoAjuste.setNombre(TEST_PREFIX + "AJUSTE_PROP");
        conceptoAjuste.setPorcentaje(new BigDecimal("-20")); // -20% del margen
        conceptoAjuste.setAplicaSobre(AplicaSobre.AJUSTE_MARGEN_PROPORCIONAL);
        conceptoAjuste = conceptoGastoRepository.save(conceptoAjuste);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoAjuste.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar AJUSTE_MARGEN_PROPORCIONAL");
        assertTrue(pvpNuevo.compareTo(pvpInicial) < 0,
                "El PVP debe disminuir al reducir proporcionalmente el margen");
    }

    // ===========================================
    // TEST 17: Concepto GASTO_POST_GANANCIA
    // ===========================================
    @Test
    @Order(17)
    @DisplayName("17. Recálculo automático con concepto GASTO_POST_GANANCIA")
    void testRecalculoConGastoPostGanancia() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto GASTO_POST_GANANCIA
        ConceptoCalculo conceptoGasto = new ConceptoCalculo();
        conceptoGasto.setNombre(TEST_PREFIX + "POST_GAN");
        conceptoGasto.setPorcentaje(new BigDecimal("5"));
        conceptoGasto.setAplicaSobre(AplicaSobre.GASTO_POST_GANANCIA);
        conceptoGasto = conceptoGastoRepository.save(conceptoGasto);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoGasto.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar GASTO_POST_GANANCIA");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar gasto post ganancia");
    }

    // ===========================================
    // TEST 18: Concepto IMPUESTO_EN_FACTOR_IMP
    // ===========================================
    @Test
    @Order(18)
    @DisplayName("18. Recálculo automático con concepto IMPUESTO_EN_FACTOR_IMP")
    void testRecalculoConImpuestoAdicional() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto IMPUESTO_EN_FACTOR_IMP (ej: IIBB)
        ConceptoCalculo conceptoImpuesto = new ConceptoCalculo();
        conceptoImpuesto.setNombre(TEST_PREFIX + "IIBB");
        conceptoImpuesto.setPorcentaje(new BigDecimal("3.5"));
        conceptoImpuesto.setAplicaSobre(AplicaSobre.IMPUESTO_EN_FACTOR_IMP);
        conceptoImpuesto = conceptoGastoRepository.save(conceptoImpuesto);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoImpuesto.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar IMPUESTO_EN_FACTOR_IMP");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar impuesto adicional");
    }

    // ===========================================
    // TEST 19: Concepto GASTO_POST_IMPUESTOS
    // ===========================================
    @Test
    @Order(19)
    @DisplayName("19. Recálculo automático con concepto GASTO_POST_IMPUESTOS")
    void testRecalculoConGastoPostImpuestos() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto GASTO_POST_IMPUESTOS
        ConceptoCalculo conceptoGasto = new ConceptoCalculo();
        conceptoGasto.setNombre(TEST_PREFIX + "POST_IMP");
        conceptoGasto.setPorcentaje(new BigDecimal("2"));
        conceptoGasto.setAplicaSobre(AplicaSobre.GASTO_POST_IMPUESTOS);
        conceptoGasto = conceptoGastoRepository.save(conceptoGasto);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoGasto.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar GASTO_POST_IMPUESTOS");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar gasto post impuestos");
    }

    // ===========================================
    // TEST 20: Concepto COSTO_OCULTO_PVP
    // ===========================================
    @Test
    @Order(20)
    @DisplayName("20. Recálculo automático con concepto COSTO_OCULTO_PVP")
    void testRecalculoConRecargoCupon() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto COSTO_OCULTO_PVP
        ConceptoCalculo conceptoCupon = new ConceptoCalculo();
        conceptoCupon.setNombre(TEST_PREFIX + "CUPON");
        conceptoCupon.setPorcentaje(new BigDecimal("5"));
        conceptoCupon.setAplicaSobre(AplicaSobre.COSTO_OCULTO_PVP);
        conceptoCupon = conceptoGastoRepository.save(conceptoCupon);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoCupon.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar COSTO_OCULTO_PVP");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al agregar costo oculto sobre PVP");
    }

    // ===========================================
    // TEST 21: Eliminar CanalConceptoCuota
    // ===========================================
    @Test
    @Order(21)
    @DisplayName("21. Recálculo automático al eliminar cuota del canal")
    void testRecalculoPorEliminarCuotaCanal() {
        // Crear una cuota adicional usando el servicio
        var cuotaCreada = canalConceptoCuotaService.crear(
                new CanalConceptoCuotaCreateDTO(canal.getId(), 3, new BigDecimal("15"), "3 cuotas"));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        entityManager.flush();
        entityManager.clear();

        // Verificar que existe precio para 3 cuotas
        var precioCuotas = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 3);
        assertTrue(precioCuotas.isPresent(), "Debe existir precio para 3 cuotas antes de eliminar");

        // Eliminar la cuota usando el servicio (borra precios de esa cuota directamente)
        canalConceptoCuotaService.eliminar(cuotaCreada.id());

        entityManager.flush();
        entityManager.clear();

        // Verificar que ya no existe precio para 3 cuotas
        precioCuotas = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 3);
        assertFalse(precioCuotas.isPresent(),
                "No debe existir precio para 3 cuotas después de eliminar la cuota");
    }

    // ===========================================
    // TEST 22: Cambio de Proveedor en Producto
    // ===========================================
    @Test
    @Order(22)
    @DisplayName("22. Recálculo automático al cambiar proveedor del producto")
    void testRecalculoPorCambioProveedorEnProducto() {
        // Crear proveedor 1
        Proveedor proveedor1 = new Proveedor();
        proveedor1.setNombre(TEST_PREFIX + "Proveedor1");
        proveedor1.setApodo(TEST_PREFIX + "P1");
        proveedor1.setFinanciacionPorcentaje(new BigDecimal("5"));
        proveedor1 = proveedorRepository.save(proveedor1);

        // Crear proveedor 2 con diferente porcentaje
        Proveedor proveedor2 = new Proveedor();
        proveedor2.setNombre(TEST_PREFIX + "Proveedor2");
        proveedor2.setApodo(TEST_PREFIX + "P2");
        proveedor2.setFinanciacionPorcentaje(new BigDecimal("15"));
        proveedor2 = proveedorRepository.save(proveedor2);

        // Crear concepto FLAG_FINANCIACION_PROVEEDOR
        ConceptoCalculo conceptoFinanciacion = new ConceptoCalculo();
        conceptoFinanciacion.setNombre(TEST_PREFIX + "FIN_PROV2");
        conceptoFinanciacion.setPorcentaje(BigDecimal.ZERO);
        conceptoFinanciacion.setAplicaSobre(AplicaSobre.FLAG_FINANCIACION_PROVEEDOR);
        conceptoFinanciacion = conceptoGastoRepository.save(conceptoFinanciacion);

        CanalConcepto ccFin = new CanalConcepto();
        ccFin.setId(new CanalConceptoId(canal.getId(), conceptoFinanciacion.getId()));
        ccFin.setCanal(canal);
        ccFin.setConcepto(conceptoFinanciacion);
        canalConceptoRepository.save(ccFin);

        // Asignar proveedor 1 al producto
        producto.setProveedor(proveedor1);
        producto = productoRepository.save(producto);
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConProveedor1 = obtenerPvpActual();

        // Cambiar a proveedor 2
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(null, null, null, null, null, proveedor2.getId()));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpConProveedor2 = obtenerPvpActual();

        assertNotEquals(pvpConProveedor1, pvpConProveedor2,
                "El PVP debe cambiar al cambiar el proveedor del producto");
    }

    // ===========================================
    // TEST 23: ProductoMargen — cambio de margen minorista
    // ===========================================
    @Test
    @Order(23)
    @DisplayName("23. Recálculo automático al cambiar margen minorista del producto")
    void testRecalculoPorCambioMargenMinorista() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Cambiar margen minorista a un valor diferente para forzar recálculo
        productoMargenService.guardar(
                new ProductoMargenDTO(
                        productoMargen.getId(),
                        producto.getId(),
                        new BigDecimal("80"),
                        new BigDecimal("30"),
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el margen minorista");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al subir el margen minorista");
    }

    // ===========================================
    // TEST 24: Múltiples productos afectados por cambio de concepto
    // ===========================================
    @Test
    @Order(24)
    @DisplayName("24. Recálculo de múltiples productos al cambiar concepto del canal")
    void testRecalculoMultiplesProductosPorCambioConcepto() {
        // Crear segundo producto
        Producto producto2 = new Producto();
        producto2.setSku(TEST_PREFIX + "002");
        producto2.setTituloDux(TEST_PREFIX + "Producto 2");
        producto2.setTituloNube(TEST_PREFIX + "Producto Test 2");
        producto2.setCosto(new BigDecimal("2000"));
        producto2.setIva(new BigDecimal("21"));
        producto2.setOrigen(producto.getOrigen());
        producto2.setTipo(producto.getTipo());
        producto2.setClasifGral(producto.getClasifGral());
        producto2 = productoRepository.save(producto2);

        // Crear margen para producto 2
        ProductoMargen margen2 = new ProductoMargen();
        margen2.setProducto(producto2);
        margen2.setMargenMinorista(new BigDecimal("40"));
        margen2.setMargenMayorista(new BigDecimal("25"));
        productoMargenRepository.save(margen2);

        entityManager.flush();

        // Calcular precios para ambos productos
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto2.getId(), canal.getId());

        entityManager.flush();
        entityManager.clear();

        BigDecimal pvp1Inicial = obtenerPvpActual();
        var precio2Opt = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto2.getId(), canal.getId(), 1);
        assertTrue(precio2Opt.isPresent(), "Debe existir precio para producto 2");
        BigDecimal pvp2Inicial = precio2Opt.get().getPvp();

        // Modificar el concepto de comisión (afecta a ambos productos)
        conceptoGastoService.actualizar(conceptoComision.getId(),
                new ConceptoCalculoUpdateDTO(
                        conceptoComision.getNombre(),
                        new BigDecimal("25"), // aumentar comisión
                        conceptoComision.getAplicaSobre().name(),
                        null,
                        null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto2.getId(), canal.getId());

        entityManager.flush();
        entityManager.clear();

        BigDecimal pvp1Nuevo = obtenerPvpActual();
        precio2Opt = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto2.getId(), canal.getId(), 1);
        assertTrue(precio2Opt.isPresent(), "Debe seguir existiendo precio para producto 2");
        BigDecimal pvp2Nuevo = precio2Opt.get().getPvp();

        assertNotEquals(pvp1Inicial, pvp1Nuevo,
                "El PVP del producto 1 debe cambiar");
        assertNotEquals(pvp2Inicial, pvp2Nuevo,
                "El PVP del producto 2 debe cambiar");
    }

    // ===========================================
    // TEST 25: Crear nueva cuota en canal
    // ===========================================
    @Test
    @Order(25)
    @DisplayName("25. Recálculo automático al crear nueva cuota en canal")
    void testRecalculoPorCrearNuevaCuota() {
        // Verificar cuotas existentes
        List<ProductoCanalPrecio> preciosAntes = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdOrderByCuotasAsc(producto.getId(), canal.getId());
        int cantidadCuotasAntes = preciosAntes.size();

        // Crear nueva cuota de 6 pagos usando el servicio
        canalConceptoCuotaService.crear(
                new CanalConceptoCuotaCreateDTO(canal.getId(), 6, new BigDecimal("25"), "6 cuotas"));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        entityManager.flush();
        entityManager.clear();

        List<ProductoCanalPrecio> preciosDespues = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdOrderByCuotasAsc(producto.getId(), canal.getId());

        assertTrue(preciosDespues.size() > cantidadCuotasAntes,
                "Debe haber más precios después de agregar cuota");

        var precio6Cuotas = preciosDespues.stream()
                .filter(p -> p.getCuotas() != null && p.getCuotas() == 6)
                .findFirst();
        assertTrue(precio6Cuotas.isPresent(),
                "Debe existir precio para 6 cuotas");
    }

    // ===========================================
    // TEST 26: Cambio en MLA (comisionPorcentaje)
    // ===========================================
    @Test
    @Order(26)
    @DisplayName("26. Recálculo automático al cambiar comisionPorcentaje del MLA")
    void testRecalculoPorCambioMlaComisionPorcentaje() {
        // Crear MLA con comisión
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA_COMISION");
        mla.setComisionPorcentaje(new BigDecimal("10"));
        mla = mlaRepository.save(mla);

        // Asignar MLA al producto
        producto.setMla(mla);
        producto = productoRepository.save(producto);

        // Crear concepto FLAG_COMISION_ML
        ConceptoCalculo conceptoComisionMl = new ConceptoCalculo();
        conceptoComisionMl.setNombre(TEST_PREFIX + "COMISION_ML");
        conceptoComisionMl.setPorcentaje(BigDecimal.ZERO);
        conceptoComisionMl.setAplicaSobre(AplicaSobre.FLAG_COMISION_ML);
        conceptoComisionMl = conceptoGastoRepository.save(conceptoComisionMl);

        CanalConcepto ccComisionMl = new CanalConcepto();
        ccComisionMl.setId(new CanalConceptoId(canal.getId(), conceptoComisionMl.getId()));
        ccComisionMl.setCanal(canal);
        ccComisionMl.setConcepto(conceptoComisionMl);
        canalConceptoRepository.save(ccComisionMl);

        // Recalcular con comisión ML
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpInicial = obtenerPvpActual();

        // Modificar porcentaje de comisión
        mlaService.actualizar(mla.getId(),
                new MlaUpdateDTO(TEST_PREFIX + "MLA_COMISION", null, null, new BigDecimal("25"), 0));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al modificar el porcentaje de comisión del MLA");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aumentar el porcentaje de comisión");
    }

    // ===========================================
    // TEST 27: Concepto DESCUENTO_PORCENTUAL
    // ===========================================
    @Test
    @Order(27)
    @DisplayName("27. Recálculo automático con concepto DESCUENTO_PORCENTUAL")
    void testRecalculoConDescuentoPorcentual() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto DESCUENTO_PORCENTUAL
        ConceptoCalculo conceptoDescuento = new ConceptoCalculo();
        conceptoDescuento.setNombre(TEST_PREFIX + "DESC_PORC");
        conceptoDescuento.setPorcentaje(new BigDecimal("15")); // 15% descuento
        conceptoDescuento.setAplicaSobre(AplicaSobre.DESCUENTO_PORCENTUAL);
        conceptoDescuento = conceptoGastoRepository.save(conceptoDescuento);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoDescuento.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar DESCUENTO_PORCENTUAL");
        assertTrue(pvpNuevo.compareTo(pvpInicial) < 0,
                "El PVP debe disminuir al aplicar descuento porcentual");
    }

    // ===========================================
    // TEST 28: Concepto INFLACION_DIVISOR_FINAL
    // ===========================================
    @Test
    @Order(28)
    @DisplayName("28. Recálculo automático con concepto INFLACION_DIVISOR_FINAL")
    void testRecalculoConInflacionDivisor() {
        BigDecimal pvpInicial = obtenerPvpActual();

        // Crear concepto INFLACION_DIVISOR_FINAL
        ConceptoCalculo conceptoInflacion = new ConceptoCalculo();
        conceptoInflacion.setNombre(TEST_PREFIX + "INFLACION");
        conceptoInflacion.setPorcentaje(new BigDecimal("10")); // 10% inflación
        conceptoInflacion.setAplicaSobre(AplicaSobre.INFLACION_DIVISOR_FINAL);
        conceptoInflacion = conceptoGastoRepository.save(conceptoInflacion);

        // Asignar al canal
        canalConceptoService.asignarConcepto(canal.getId(), conceptoInflacion.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpNuevo = obtenerPvpActual();

        assertNotEquals(pvpInicial, pvpNuevo,
                "El PVP debe cambiar al agregar INFLACION_DIVISOR_FINAL");
        assertTrue(pvpNuevo.compareTo(pvpInicial) > 0,
                "El PVP debe aumentar al aplicar inflación como divisor");
    }

    // ===========================================
    // TEST 29: PrecioInflado (crear asignación)
    // ===========================================
    @Test
    @Order(29)
    @DisplayName("29. Recálculo automático al crear asignación de precio inflado")
    void testRecalculoPorCrearPrecioInflado() {
        // Crear concepto FLAG_APLICAR_PRECIO_INFLADO para habilitar la funcionalidad
        ConceptoCalculo conceptoPrecioInflado = new ConceptoCalculo();
        conceptoPrecioInflado.setNombre(TEST_PREFIX + "PRECIO_INFLADO");
        conceptoPrecioInflado.setPorcentaje(BigDecimal.ZERO);
        conceptoPrecioInflado.setAplicaSobre(AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        conceptoPrecioInflado = conceptoGastoRepository.save(conceptoPrecioInflado);

        CanalConcepto ccInflado = new CanalConcepto();
        ccInflado.setId(new CanalConceptoId(canal.getId(), conceptoPrecioInflado.getId()));
        ccInflado.setCanal(canal);
        ccInflado.setConcepto(conceptoPrecioInflado);
        canalConceptoRepository.save(ccInflado);

        // Recalcular para incluir el concepto
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // Sin precio inflado asignado, pvpInflado debe ser null
        BigDecimal pvpInfladoAntes = obtenerPvpInfladoActual();
        assertNull(pvpInfladoAntes, "pvpInflado debe ser null sin asignación");

        // Crear un precio inflado maestro (MULTIPLICADOR x1.5)
        PrecioInflado precioInflado = new PrecioInflado();
        precioInflado.setCodigo(TEST_PREFIX + "MULT15");
        precioInflado.setTipo(TipoPrecioInflado.MULTIPLICADOR);
        precioInflado.setValor(new BigDecimal("1.500"));
        precioInflado = precioInfladoRepository.save(precioInflado);

        entityManager.flush();

        // Asignar precio inflado al producto+canal (esto dispara recálculo)
        productoCanalPrecioInfladoService.crear(
                new ProductoCanalPrecioInfladoCreateDTO(
                        producto.getId(), canal.getId(), precioInflado.getId(),
                        true, null, null, null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoDespues = obtenerPvpInfladoActual();
        BigDecimal pvp = obtenerPvpActual();

        assertNotNull(pvpInfladoDespues,
                "pvpInflado debe tener valor después de asignar precio inflado");
        assertTrue(pvpInfladoDespues.compareTo(pvp) > 0,
                "pvpInflado debe ser mayor que pvp con multiplicador 1.5");
    }

    // ===========================================
    // TEST 30: PrecioInflado (actualizar asignación)
    // ===========================================
    @Test
    @Order(30)
    @DisplayName("30. Recálculo automático al actualizar asignación de precio inflado")
    void testRecalculoPorActualizarPrecioInflado() {
        // Crear concepto FLAG_APLICAR_PRECIO_INFLADO
        ConceptoCalculo conceptoPrecioInflado = new ConceptoCalculo();
        conceptoPrecioInflado.setNombre(TEST_PREFIX + "PRECIO_INFLADO2");
        conceptoPrecioInflado.setPorcentaje(BigDecimal.ZERO);
        conceptoPrecioInflado.setAplicaSobre(AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        conceptoPrecioInflado = conceptoGastoRepository.save(conceptoPrecioInflado);

        CanalConcepto ccInflado = new CanalConcepto();
        ccInflado.setId(new CanalConceptoId(canal.getId(), conceptoPrecioInflado.getId()));
        ccInflado.setCanal(canal);
        ccInflado.setConcepto(conceptoPrecioInflado);
        canalConceptoRepository.save(ccInflado);

        // Crear dos precios inflados maestros
        PrecioInflado precioInflado1 = new PrecioInflado();
        precioInflado1.setCodigo(TEST_PREFIX + "MULT12");
        precioInflado1.setTipo(TipoPrecioInflado.MULTIPLICADOR);
        precioInflado1.setValor(new BigDecimal("1.200"));
        precioInflado1 = precioInfladoRepository.save(precioInflado1);

        PrecioInflado precioInflado2 = new PrecioInflado();
        precioInflado2.setCodigo(TEST_PREFIX + "MULT20");
        precioInflado2.setTipo(TipoPrecioInflado.MULTIPLICADOR);
        precioInflado2.setValor(new BigDecimal("2.000"));
        precioInflado2 = precioInfladoRepository.save(precioInflado2);

        entityManager.flush();

        // Asignar precio inflado 1 (x1.2)
        productoCanalPrecioInfladoService.crear(
                new ProductoCanalPrecioInfladoCreateDTO(
                        producto.getId(), canal.getId(), precioInflado1.getId(),
                        true, null, null, null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoConMult12 = obtenerPvpInfladoActual();
        assertNotNull(pvpInfladoConMult12, "pvpInflado debe existir con multiplicador 1.2");

        // Actualizar a precio inflado 2 (x2.0 → pvpInflado debe aumentar)
        productoCanalPrecioInfladoService.actualizar(
                producto.getId(), canal.getId(),
                new ProductoCanalPrecioInfladoUpdateDTO(
                        precioInflado2.getId(), true, null, null, null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoConMult20 = obtenerPvpInfladoActual();
        assertNotNull(pvpInfladoConMult20, "pvpInflado debe existir con multiplicador 2.0");

        assertNotEquals(pvpInfladoConMult12, pvpInfladoConMult20,
                "pvpInflado debe cambiar al actualizar el precio inflado asignado");
        assertTrue(pvpInfladoConMult20.compareTo(pvpInfladoConMult12) > 0,
                "pvpInflado debe aumentar al cambiar a un multiplicador mayor");
    }

    // ===========================================
    // TEST 31: PrecioInflado (eliminar asignación)
    // ===========================================
    @Test
    @Order(31)
    @DisplayName("31. Recálculo automático al eliminar asignación de precio inflado")
    void testRecalculoPorEliminarPrecioInflado() {
        // Crear concepto FLAG_APLICAR_PRECIO_INFLADO
        ConceptoCalculo conceptoPrecioInflado = new ConceptoCalculo();
        conceptoPrecioInflado.setNombre(TEST_PREFIX + "PRECIO_INFLADO3");
        conceptoPrecioInflado.setPorcentaje(BigDecimal.ZERO);
        conceptoPrecioInflado.setAplicaSobre(AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        conceptoPrecioInflado = conceptoGastoRepository.save(conceptoPrecioInflado);

        CanalConcepto ccInflado = new CanalConcepto();
        ccInflado.setId(new CanalConceptoId(canal.getId(), conceptoPrecioInflado.getId()));
        ccInflado.setCanal(canal);
        ccInflado.setConcepto(conceptoPrecioInflado);
        canalConceptoRepository.save(ccInflado);

        // Crear precio inflado maestro
        PrecioInflado precioInflado = new PrecioInflado();
        precioInflado.setCodigo(TEST_PREFIX + "MULT18");
        precioInflado.setTipo(TipoPrecioInflado.MULTIPLICADOR);
        precioInflado.setValor(new BigDecimal("1.800"));
        precioInflado = precioInfladoRepository.save(precioInflado);

        entityManager.flush();

        // Asignar precio inflado (x1.8)
        productoCanalPrecioInfladoService.crear(
                new ProductoCanalPrecioInfladoCreateDTO(
                        producto.getId(), canal.getId(), precioInflado.getId(),
                        true, null, null, null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoConAsignacion = obtenerPvpInfladoActual();
        assertNotNull(pvpInfladoConAsignacion, "pvpInflado debe existir con asignación");

        // Eliminar la asignación (esto dispara recálculo)
        productoCanalPrecioInfladoService.eliminar(producto.getId(), canal.getId());
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoSinAsignacion = obtenerPvpInfladoActual();

        assertNull(pvpInfladoSinAsignacion,
                "pvpInflado debe ser null después de eliminar la asignación");
    }

    // ===========================================
    // TEST 32: CanalConceptoRegla EXCLUIR
    // ===========================================
    @Test
    @Order(32)
    @DisplayName("32. CanalConceptoRegla EXCLUIR excluye concepto del cálculo")
    void testReglaExcluirConceptoDelCalculo() {
        // Dar una marca al producto para poder crear una regla que la use
        Marca marca = new Marca();
        marca.setNombre(TEST_PREFIX + "MarcaX");
        marca = marcaRepository.save(marca);

        producto.setMarca(marca);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Recalcular con la comisión activa
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConComision = obtenerPvpActual();

        // Crear regla EXCLUIR: el concepto de comisión NO aplica para esta marca
        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoComision.getId(),
                        "EXCLUIR",
                        null, null, null,
                        marca.getId(),
                        null,
                        null
                ));

        // Recalcular manualmente (las reglas no disparan recálculo automático)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpSinComision = obtenerPvpActual();

        assertNotEquals(pvpConComision, pvpSinComision,
                "El PVP debe cambiar cuando una regla EXCLUIR elimina la comisión");
        assertTrue(pvpSinComision.compareTo(pvpConComision) < 0,
                "El PVP debe disminuir al excluir la comisión del cálculo");
    }

    // ===========================================
    // TEST 33: Cambio de Tipo en Producto (afecta reglas)
    // ===========================================
    @Test
    @Order(33)
    @DisplayName("33. Recálculo automático al cambiar tipo del Producto (afecta reglas de concepto)")
    void testRecalculoPorCambioTipoProducto() {
        // Crear un tipo específico y asignarlo al producto
        Tipo tipoA = new Tipo();
        tipoA.setNombre(TEST_PREFIX + "TipoA");
        tipoA = tipoRepository.save(tipoA);

        Tipo tipoB = new Tipo();
        tipoB.setNombre(TEST_PREFIX + "TipoB");
        tipoB = tipoRepository.save(tipoB);

        producto.setTipo(tipoA);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Crear concepto adicional de gasto sobre costo
        ConceptoCalculo conceptoGasto = new ConceptoCalculo();
        conceptoGasto.setNombre(TEST_PREFIX + "GASTO_TIPO");
        conceptoGasto.setPorcentaje(new BigDecimal("15"));
        conceptoGasto.setAplicaSobre(AplicaSobre.GASTO_SOBRE_COSTO);
        conceptoGasto = conceptoGastoRepository.save(conceptoGasto);

        CanalConcepto ccGasto = new CanalConcepto();
        ccGasto.setId(new CanalConceptoId(canal.getId(), conceptoGasto.getId()));
        ccGasto.setCanal(canal);
        ccGasto.setConcepto(conceptoGasto);
        canalConceptoRepository.save(ccGasto);

        // Crear regla INCLUIR: concepto SOLO aplica para tipoA
        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoGasto.getId(),
                        "INCLUIR",
                        tipoA.getId(), null, null,
                        null,
                        null,
                        null
                ));

        entityManager.flush();

        // Calcular con tipoA (concepto aplica)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConTipoA = obtenerPvpActual();

        // Cambiar tipo a tipoB (concepto ya NO aplica)
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(null, null, null, tipoB.getId(), null, null));

        // Recalcular manualmente ya que las reglas filtran por tipo
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConTipoB = obtenerPvpActual();

        assertNotEquals(pvpConTipoA, pvpConTipoB,
                "El PVP debe cambiar al cambiar el tipo del producto (afecta reglas de concepto)");
        assertTrue(pvpConTipoB.compareTo(pvpConTipoA) < 0,
                "El PVP debe disminuir al excluir el gasto sobre costo por cambio de tipo");
    }

    // ===========================================
    // TEST 34: Cambio de Marca en Producto (afecta reglas)
    // ===========================================
    @Test
    @Order(34)
    @DisplayName("34. Recálculo automático al cambiar marca del Producto (afecta reglas de concepto)")
    void testRecalculoPorCambioMarcaProducto() {
        // Crear marcas
        Marca marcaA = new Marca();
        marcaA.setNombre(TEST_PREFIX + "MarcaA");
        marcaA = marcaRepository.save(marcaA);

        Marca marcaB = new Marca();
        marcaB.setNombre(TEST_PREFIX + "MarcaB");
        marcaB = marcaRepository.save(marcaB);

        producto.setMarca(marcaA);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Crear concepto con regla EXCLUIR para marcaA
        ConceptoCalculo conceptoExcl = new ConceptoCalculo();
        conceptoExcl.setNombre(TEST_PREFIX + "COMISION_MARCA");
        conceptoExcl.setPorcentaje(new BigDecimal("8"));
        conceptoExcl.setAplicaSobre(AplicaSobre.COMISION_SOBRE_PVP);
        conceptoExcl = conceptoGastoRepository.save(conceptoExcl);

        CanalConcepto ccExcl = new CanalConcepto();
        ccExcl.setId(new CanalConceptoId(canal.getId(), conceptoExcl.getId()));
        ccExcl.setCanal(canal);
        ccExcl.setConcepto(conceptoExcl);
        canalConceptoRepository.save(ccExcl);

        // Regla EXCLUIR: el concepto NO aplica para marcaA
        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoExcl.getId(),
                        "EXCLUIR",
                        null, null, null,
                        marcaA.getId(),
                        null,
                        null
                ));

        entityManager.flush();

        // Con marcaA: comisión EXCLUIDA → PVP menor
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConMarcaA = obtenerPvpActual();

        // Cambiar a marcaB: regla ya no aplica → comisión se incluye → PVP sube
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(null, null, marcaB.getId(), null, null, null));

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConMarcaB = obtenerPvpActual();

        assertNotEquals(pvpConMarcaA, pvpConMarcaB,
                "El PVP debe cambiar al cambiar la marca del producto (afecta reglas de concepto)");
        assertTrue(pvpConMarcaB.compareTo(pvpConMarcaA) > 0,
                "El PVP debe aumentar al incluir la comisión que antes estaba excluida por marca");
    }

    // ===========================================
    // TEST 35: Cambio de ClasifGral en Producto (afecta reglas)
    // ===========================================
    @Test
    @Order(35)
    @DisplayName("35. Recálculo automático al cambiar clasifGral del Producto (afecta reglas de concepto)")
    void testRecalculoPorCambioClasifGralProducto() {
        // Crear clasificaciones generales
        ClasifGral clasifA = new ClasifGral();
        clasifA.setNombre(TEST_PREFIX + "ClasifA");
        clasifA = clasifGralRepository.save(clasifA);

        ClasifGral clasifB = new ClasifGral();
        clasifB.setNombre(TEST_PREFIX + "ClasifB");
        clasifB = clasifGralRepository.save(clasifB);

        producto.setClasifGral(clasifA);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Crear concepto con regla INCLUIR para clasifA
        ConceptoCalculo conceptoClasif = new ConceptoCalculo();
        conceptoClasif.setNombre(TEST_PREFIX + "GASTO_CLASIF");
        conceptoClasif.setPorcentaje(new BigDecimal("12"));
        conceptoClasif.setAplicaSobre(AplicaSobre.GASTO_SOBRE_COSTO);
        conceptoClasif = conceptoGastoRepository.save(conceptoClasif);

        CanalConcepto ccClasif = new CanalConcepto();
        ccClasif.setId(new CanalConceptoId(canal.getId(), conceptoClasif.getId()));
        ccClasif.setCanal(canal);
        ccClasif.setConcepto(conceptoClasif);
        canalConceptoRepository.save(ccClasif);

        // Regla INCLUIR: concepto SOLO aplica para clasifA
        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoClasif.getId(),
                        "INCLUIR",
                        null, null, clasifA.getId(),
                        null,
                        null,
                        null
                ));

        entityManager.flush();

        // Con clasifA: gasto INCLUIDO → PVP mayor
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConClasifA = obtenerPvpActual();

        // Cambiar a clasifB: regla ya no aplica → gasto excluido → PVP baja
        productoService.actualizar(producto.getId(),
                buildProductoUpdate(null, null, null, null, clasifB.getId(), null));

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConClasifB = obtenerPvpActual();

        assertNotEquals(pvpConClasifA, pvpConClasifB,
                "El PVP debe cambiar al cambiar la clasificación general del producto");
        assertTrue(pvpConClasifB.compareTo(pvpConClasifA) < 0,
                "El PVP debe disminuir al excluir el gasto sobre costo por cambio de clasifGral");
    }

    // ===========================================
    // TEST 36: CanalConceptoRegla INCLUIR
    // ===========================================
    @Test
    @Order(36)
    @DisplayName("36. CanalConceptoRegla INCLUIR incluye concepto solo cuando se cumplen condiciones")
    void testReglaIncluirConceptoEnCalculo() {
        // Crear marca y asignarla al producto
        Marca marca = new Marca();
        marca.setNombre(TEST_PREFIX + "MarcaIncluir");
        marca = marcaRepository.save(marca);

        producto.setMarca(marca);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Crear concepto con regla INCLUIR para esa marca
        ConceptoCalculo conceptoIncluir = new ConceptoCalculo();
        conceptoIncluir.setNombre(TEST_PREFIX + "GASTO_INCLUIR");
        conceptoIncluir.setPorcentaje(new BigDecimal("20"));
        conceptoIncluir.setAplicaSobre(AplicaSobre.GASTO_SOBRE_COSTO);
        conceptoIncluir = conceptoGastoRepository.save(conceptoIncluir);

        CanalConcepto ccIncluir = new CanalConcepto();
        ccIncluir.setId(new CanalConceptoId(canal.getId(), conceptoIncluir.getId()));
        ccIncluir.setCanal(canal);
        ccIncluir.setConcepto(conceptoIncluir);
        canalConceptoRepository.save(ccIncluir);

        entityManager.flush();

        // PVP antes de la regla (concepto aplica sin reglas)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpSinRegla = obtenerPvpActual();

        // Crear regla INCLUIR para otra marca (no la del producto)
        Marca marcaOtra = new Marca();
        marcaOtra.setNombre(TEST_PREFIX + "MarcaOtra");
        marcaOtra = marcaRepository.save(marcaOtra);

        canalConceptoReglaService.crear(
                new CanalConceptoReglaCreateDTO(
                        canal.getId(),
                        conceptoIncluir.getId(),
                        "INCLUIR",
                        null, null, null,
                        marcaOtra.getId(),
                        null,
                        null
                ));

        entityManager.flush();

        // Recalcular: ahora la regla INCLUIR exige marca=MarcaOtra, pero producto tiene marca=MarcaIncluir
        // → concepto NO aplica → PVP debe bajar
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConReglaNoMatch = obtenerPvpActual();

        assertTrue(pvpConReglaNoMatch.compareTo(pvpSinRegla) < 0,
                "El PVP debe disminuir cuando la regla INCLUIR no se cumple y el concepto se excluye");
    }

    // ===========================================
    // TEST 37: FLAG_INCLUIR_ENVIO
    // ===========================================
    @Test
    @Order(37)
    @DisplayName("37. Recálculo con FLAG_INCLUIR_ENVIO suma precioEnvio del MLA al PVP")
    void testRecalculoConFlagIncluirEnvio() {
        // Crear MLA con precioEnvio
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA_ENVIO");
        mla.setPrecioEnvio(new BigDecimal("500"));
        mla = mlaRepository.save(mla);

        producto.setMla(mla);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // PVP sin FLAG_INCLUIR_ENVIO
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpSinEnvio = obtenerPvpActual();

        // Agregar concepto FLAG_INCLUIR_ENVIO al canal
        ConceptoCalculo conceptoEnvio = new ConceptoCalculo();
        conceptoEnvio.setNombre(TEST_PREFIX + "ENVIO");
        conceptoEnvio.setPorcentaje(BigDecimal.ZERO);
        conceptoEnvio.setAplicaSobre(AplicaSobre.FLAG_INCLUIR_ENVIO);
        conceptoEnvio = conceptoGastoRepository.save(conceptoEnvio);

        CanalConcepto ccEnvio = new CanalConcepto();
        ccEnvio.setId(new CanalConceptoId(canal.getId(), conceptoEnvio.getId()));
        ccEnvio.setCanal(canal);
        ccEnvio.setConcepto(conceptoEnvio);
        canalConceptoRepository.save(ccEnvio);
        entityManager.flush();

        // PVP con FLAG_INCLUIR_ENVIO
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConEnvio = obtenerPvpActual();

        assertTrue(pvpConEnvio.compareTo(pvpSinEnvio) > 0,
                "El PVP debe aumentar al incluir el costo de envío del MLA");
    }

    // ===========================================
    // TEST 38: FLAG_COMISION_ML
    // ===========================================
    @Test
    @Order(38)
    @DisplayName("38. Recálculo con FLAG_COMISION_ML usa comisionPorcentaje del MLA")
    void testRecalculoConFlagComisionMl() {
        // Crear MLA con comisiónPorcentaje
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA_COMML");
        mla.setComisionPorcentaje(new BigDecimal("14.5"));
        mla = mlaRepository.save(mla);

        producto.setMla(mla);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // PVP sin FLAG_COMISION_ML
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpSinComisionMl = obtenerPvpActual();

        // Agregar concepto FLAG_COMISION_ML al canal
        ConceptoCalculo conceptoComisionMl = new ConceptoCalculo();
        conceptoComisionMl.setNombre(TEST_PREFIX + "COMISION_ML");
        conceptoComisionMl.setPorcentaje(BigDecimal.ZERO);
        conceptoComisionMl.setAplicaSobre(AplicaSobre.FLAG_COMISION_ML);
        conceptoComisionMl = conceptoGastoRepository.save(conceptoComisionMl);

        CanalConcepto ccComisionMl = new CanalConcepto();
        ccComisionMl.setId(new CanalConceptoId(canal.getId(), conceptoComisionMl.getId()));
        ccComisionMl.setCanal(canal);
        ccComisionMl.setConcepto(conceptoComisionMl);
        canalConceptoRepository.save(ccComisionMl);
        entityManager.flush();

        // PVP con FLAG_COMISION_ML (comisión del MLA se suma como gasto sobre PVP)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConComisionMl = obtenerPvpActual();

        assertTrue(pvpConComisionMl.compareTo(pvpSinComisionMl) > 0,
                "El PVP debe aumentar al incluir la comisión ML del MLA como gasto sobre PVP");
    }

    // ===========================================
    // TEST 39: FLAG_COMISION_ML con naturaleza override INFLACION
    // (antes era FLAG_INFLACION_ML, ahora unificado en FLAG_COMISION_ML con override)
    // ===========================================
    @Test
    @Order(39)
    @DisplayName("39. FLAG_COMISION_ML con naturaleza INFLACION infla PVP sin contar como costo")
    void testRecalculoConComisionMlComoInflacion() {
        // Crear MLA con comisiónPorcentaje
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA_INFML");
        mla.setComisionPorcentaje(new BigDecimal("12"));
        mla = mlaRepository.save(mla);

        producto.setMla(mla);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // PVP sin comisión ML
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpSinInflacion = obtenerPvpActual();

        // Agregar concepto FLAG_COMISION_ML con naturaleza INFLACION (override) — infla PVP pero NO cuenta como costo
        ConceptoCalculo conceptoInflacionMl = new ConceptoCalculo();
        conceptoInflacionMl.setNombre(TEST_PREFIX + "INFLACION_ML");
        conceptoInflacionMl.setPorcentaje(BigDecimal.ZERO);
        conceptoInflacionMl.setAplicaSobre(AplicaSobre.FLAG_COMISION_ML);
        conceptoInflacionMl.setNaturaleza(NaturalezaConcepto.INFLACION);
        conceptoInflacionMl = conceptoGastoRepository.save(conceptoInflacionMl);

        CanalConcepto ccInflacionMl = new CanalConcepto();
        ccInflacionMl.setId(new CanalConceptoId(canal.getId(), conceptoInflacionMl.getId()));
        ccInflacionMl.setCanal(canal);
        ccInflacionMl.setConcepto(conceptoInflacionMl);
        canalConceptoRepository.save(ccInflacionMl);
        entityManager.flush();

        // PVP con FLAG_COMISION_ML
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        BigDecimal pvpConInflacion = obtenerPvpActual();

        assertTrue(pvpConInflacion.compareTo(pvpSinInflacion) > 0,
                "El PVP debe aumentar al aplicar comisión ML del MLA");
    }

    // ===========================================
    // TEST 40: Modificar regla PrecioInflado (valor)
    // ===========================================
    @Test
    @Order(40)
    @DisplayName("40. Recálculo automático al modificar valor de regla PrecioInflado")
    void testRecalculoPorCambioValorReglaPrecioInflado() {
        // Agregar concepto FLAG_APLICAR_PRECIO_INFLADO al canal
        ConceptoCalculo conceptoPrecioInflado = new ConceptoCalculo();
        conceptoPrecioInflado.setNombre(TEST_PREFIX + "PRECIO_INFLADO_REGLA");
        conceptoPrecioInflado.setPorcentaje(BigDecimal.ZERO);
        conceptoPrecioInflado.setAplicaSobre(AplicaSobre.FLAG_APLICAR_PRECIO_INFLADO);
        conceptoPrecioInflado = conceptoGastoRepository.save(conceptoPrecioInflado);

        CanalConcepto ccInflado = new CanalConcepto();
        ccInflado.setId(new CanalConceptoId(canal.getId(), conceptoPrecioInflado.getId()));
        ccInflado.setCanal(canal);
        ccInflado.setConcepto(conceptoPrecioInflado);
        canalConceptoRepository.save(ccInflado);

        // Crear regla de precio inflado con multiplicador 1.5
        PrecioInflado precioInflado = new PrecioInflado();
        precioInflado.setCodigo(TEST_PREFIX + "MULT15R");
        precioInflado.setTipo(TipoPrecioInflado.MULTIPLICADOR);
        precioInflado.setValor(new BigDecimal("1.500"));
        precioInflado = precioInfladoRepository.save(precioInflado);

        // Asignar al producto/canal
        productoCanalPrecioInfladoService.crear(
                new ProductoCanalPrecioInfladoCreateDTO(
                        producto.getId(), canal.getId(), precioInflado.getId(),
                        true, null, null, null
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoInicial = obtenerPvpInfladoActual();
        assertNotNull(pvpInfladoInicial, "pvpInflado debe existir con asignación");

        // Modificar el valor de la regla de 1.5 a 2.0 (usando el service que dispara recálculo)
        precioInfladoService.actualizar(precioInflado.getId(),
                new PrecioInfladoUpdateDTO(
                        precioInflado.getCodigo(),
                        TipoPrecioInflado.MULTIPLICADOR,
                        new BigDecimal("2.000")
                ));
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        BigDecimal pvpInfladoNuevo = obtenerPvpInfladoActual();

        assertNotNull(pvpInfladoNuevo, "pvpInflado debe seguir existiendo");
        assertNotEquals(pvpInfladoInicial, pvpInfladoNuevo,
                "pvpInflado debe cambiar al modificar el valor de la regla");
        assertTrue(pvpInfladoNuevo.compareTo(pvpInfladoInicial) > 0,
                "pvpInflado debe aumentar al subir el multiplicador de 1.5 a 2.0");
    }

    // ===========================================
    // TEST 41: CanalRegla EXCLUIR por tag borra precios existentes
    // ===========================================
    @Test
    @Order(41)
    @DisplayName("41. CanalRegla EXCLUIR tag borra ProductoCanalPrecio y lo ignora al recalcular")
    void testCanalReglaExcluirTagBorraPrecios() {
        // Setup: el producto empieza sin tag y tiene precio en el canal (del setUp)
        var precioAntes = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1);
        assertTrue(precioAntes.isPresent(), "Debe existir precio inicial del setUp");

        // Cambiar tag del producto a MAQUINA
        producto.setTag(Tag.MAQUINA);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Crear regla EXCLUIR tag=MAQUINA sobre el canal (dispara recálculo completo async)
        CanalReglaDTO regla = canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(),
                "EXCLUIR",
                Tag.MAQUINA,
                null, null, null, null, null, null
        ));
        assertNotNull(regla.id());

        // Forzar el recálculo sincrónicamente (el async post-commit no corre en test @Transactional)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // El precio debe haber sido borrado porque el producto ya no aplica al canal
        var precioDespues = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1);
        assertFalse(precioDespues.isPresent(),
                "El precio debe borrarse cuando el producto queda excluido por canal_regla");
    }

    // ===========================================
    // TEST 42: Eliminar CanalRegla restaura el precio al recalcular
    // ===========================================
    @Test
    @Order(42)
    @DisplayName("42. Eliminar CanalRegla permite que el producto vuelva a tener precio en el canal")
    void testEliminarCanalReglaRestauraPrecio() {
        // Producto con tag MAQUINA y regla EXCLUIR MAQUINA → sin precio
        producto.setTag(Tag.MAQUINA);
        producto = productoRepository.save(producto);

        CanalReglaDTO regla = canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", Tag.MAQUINA,
                null, null, null, null, null, null
        ));
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Precondición: producto no debe tener precio con la regla EXCLUIR activa");

        // Eliminar la regla
        canalReglaService.eliminar(regla.id());
        entityManager.flush();

        // Forzar recálculo (el async post-commit no corre aquí)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // El precio debe haberse creado nuevamente
        var precioFinal = productoCanalPrecioRepository
                .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1);
        assertTrue(precioFinal.isPresent(),
                "El precio debe crearse al eliminar la regla EXCLUIR que lo bloqueaba");
    }

    // ===========================================
    // TEST 43: Cambio de tag en Producto respeta CanalRegla (tag nuevo excluido)
    // ===========================================
    @Test
    @Order(43)
    @DisplayName("43. Cambio de tag del producto a uno excluido borra su precio en el canal")
    void testCambioTagProductoHaciaExcluidoBorraPrecio() {
        // Regla EXCLUIR tag=REPUESTO activa en el canal
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", Tag.REPUESTO,
                null, null, null, null, null, null
        ));

        // Producto con tag=MENAJE (no excluido) tiene precio
        producto.setTag(Tag.MENAJE);
        producto = productoRepository.save(producto);
        entityManager.flush();
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertTrue(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Producto MENAJE debe tener precio");

        // Cambiar tag a REPUESTO (que sí está excluido)
        producto.setTag(Tag.REPUESTO);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Forzar recálculo (simula lo que hace recalcularProductoEnTodosLosCanales iterando todos los canales)
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // El precio debe haberse borrado
        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "El precio debe borrarse cuando el producto cambia a un tag excluido");
    }

    // ===========================================
    // TEST 45: CanalRegla por id_marca
    // ===========================================
    @Test
    @Order(45)
    @DisplayName("45. CanalRegla EXCLUIR por id_marca borra precio del producto con esa marca")
    void testCanalReglaExcluirPorMarca() {
        Marca marca = new Marca();
        marca.setNombre(TEST_PREFIX + "MarcaExcluida");
        marca = marcaRepository.save(marca);

        producto.setMarca(marca);
        producto = productoRepository.save(producto);
        entityManager.flush();

        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", null,
                null, marca.getId(), null, null, null, null
        ));
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "El precio debe borrarse con regla EXCLUIR por marca que matchea el producto");
    }

    // ===========================================
    // TEST 46: CanalRegla por id_producto (override puntual)
    // ===========================================
    @Test
    @Order(46)
    @DisplayName("46. CanalRegla EXCLUIR por id_producto aplica solo a ese producto")
    void testCanalReglaExcluirPorProductoEspecifico() {
        // Producto tiene precio inicial del setUp
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", null,
                null, null, null, null,
                producto.getId(), // id_producto específico
                null
        ));
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "El precio debe borrarse con regla EXCLUIR por id_producto específico");
    }

    // ===========================================
    // TEST 47: CanalRegla por tieneEnvio (según MLA.precioEnvio)
    // ===========================================
    @Test
    @Order(47)
    @DisplayName("47. CanalRegla EXCLUIR tieneEnvio=FALSE excluye productos sin envío")
    void testCanalReglaExcluirPorTieneEnvioFalse() {
        // Producto sin MLA (sin envío)
        assertNull(producto.getMla(), "Producto debe empezar sin MLA");

        // Regla EXCLUIR productos que NO tienen envío
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", null,
                null, null, null, null, null,
                false // tiene_envio = false → aplica a productos sin envío
        ));
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Producto sin envío debe quedar excluido por la regla tieneEnvio=false");

        // Asignar MLA con precioEnvio > 0 → ahora SÍ tiene envío → regla no aplica
        Mla mla = new Mla();
        mla.setMla(TEST_PREFIX + "MLA_ENVIO_TEST");
        mla.setPrecioEnvio(new BigDecimal("500"));
        mla = mlaRepository.save(mla);
        producto.setMla(mla);
        producto = productoRepository.save(producto);
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertTrue(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Producto con envío debe tener precio (no cumple EXCLUIR tieneEnvio=false)");
    }

    // ===========================================
    // TEST 48: Combinación INCLUIR + EXCLUIR
    // ===========================================
    @Test
    @Order(48)
    @DisplayName("48. INCLUIR + EXCLUIR: producto cumple INCLUIR pero es excluido por EXCLUIR")
    void testCanalReglaIncluirExcluirCombinados() {
        Tipo tipoPermitido = new Tipo();
        tipoPermitido.setNombre(TEST_PREFIX + "TipoIncl");
        tipoPermitido = tipoRepository.save(tipoPermitido);

        Marca marcaBloqueada = new Marca();
        marcaBloqueada.setNombre(TEST_PREFIX + "MarcaExcl");
        marcaBloqueada = marcaRepository.save(marcaBloqueada);

        producto.setTipo(tipoPermitido);
        producto.setMarca(marcaBloqueada);
        producto = productoRepository.save(producto);
        entityManager.flush();

        // Regla INCLUIR por tipo (cumple) + regla EXCLUIR por marca (también cumple)
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "INCLUIR", null,
                tipoPermitido.getId(), null, null, null, null, null
        ));
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "EXCLUIR", null,
                null, marcaBloqueada.getId(), null, null, null, null
        ));
        entityManager.flush();

        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());

        // EXCLUIR gana: el producto queda fuera aunque cumpla INCLUIR
        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "EXCLUIR debe prevalecer sobre INCLUIR cuando ambos se cumplen");
    }

    // ===========================================
    // TEST 44: CanalRegla INCLUIR por tipo limita qué productos tienen precio
    // ===========================================
    @Test
    @Order(44)
    @DisplayName("44. CanalRegla INCLUIR por tipo solo permite productos del tipo indicado")
    void testCanalReglaIncluirPorTipoLimitaProductos() {
        // Crear dos tipos
        Tipo tipoPermitido = new Tipo();
        tipoPermitido.setNombre(TEST_PREFIX + "TipoPermitido");
        tipoPermitido = tipoRepository.save(tipoPermitido);

        Tipo tipoBloqueado = new Tipo();
        tipoBloqueado.setNombre(TEST_PREFIX + "TipoBloqueado");
        tipoBloqueado = tipoRepository.save(tipoBloqueado);

        // Regla INCLUIR: solo productos de tipoPermitido van al canal
        canalReglaService.crear(new CanalReglaCreateDTO(
                canal.getId(), "INCLUIR", null,
                tipoPermitido.getId(), null, null, null, null, null
        ));
        entityManager.flush();

        // Producto del tipo permitido → tiene precio
        producto.setTipo(tipoPermitido);
        producto = productoRepository.save(producto);
        entityManager.flush();
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertTrue(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Producto de tipo permitido debe tener precio (regla INCLUIR cumple)");

        // Cambiar al tipo bloqueado → pierde el precio
        producto.setTipo(tipoBloqueado);
        producto = productoRepository.save(producto);
        entityManager.flush();
        calculoPrecioService.recalcularYGuardarPrecioCanalTodasCuotas(producto.getId(), canal.getId());
        assertFalse(productoCanalPrecioRepository
                        .findByProductoIdAndCanalIdAndCuotas(producto.getId(), canal.getId(), 1).isPresent(),
                "Producto de tipo bloqueado no debe tener precio (regla INCLUIR no cumple)");
    }
}

