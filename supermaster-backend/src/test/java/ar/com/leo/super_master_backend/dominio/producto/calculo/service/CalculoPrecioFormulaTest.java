package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoCuota;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoId;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoRepository;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.repository.ConceptoCalculoRepository;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.PrecioCalculadoDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.SimulacionPrecioInputDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.dto.SimulacionResultadoDTO;
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
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de la FÓRMULA de cálculo de precios ({@link CalculoPrecioServiceImpl}).
 *
 * A diferencia de {@code RecalculoAutomaticoIntegrationTest} (que verifica los disparadores
 * de recálculo en cascada), este test fija la atención en los VALORES que produce la fórmula:
 * el PVP y las métricas contables (costoProducto, costosVenta, ingresoNetoVendedor, ganancia,
 * márgenes, markup) para escenarios controlados, incluyendo casos borde (IVA 0, margen 0,
 * costo 0).
 *
 * Estrategia: se seedea un canal mínimo y determinista con tres conceptos
 * (FLAG_USAR_MARGEN_MINORISTA + FLAG_APLICAR_IVA + COMISION_SOBRE_PVP 13%) y se usa el camino
 * de simulación ({@link CalculoPrecioService#simularPrecioCompleto}), que ejecuta el motor real
 * sobre un producto hipotético en memoria. Todo corre dentro de una transacción con rollback,
 * así que no deja datos en la BD.
 *
 * Fórmula esperada (sin cuotas):
 *   PVP = (costo × (1 + margen/100) × (1 + iva/100)) / (1 − comision/100)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalculoPrecioFormulaTest {

    @Autowired
    private CanalRepository canalRepository;

    @Autowired
    private ConceptoCalculoRepository conceptoCalculoRepository;

    @Autowired
    private CanalConceptoRepository canalConceptoRepository;

    @Autowired
    private CanalConceptoCuotaRepository canalConceptoCuotaRepository;

    @Autowired
    private CalculoPrecioService calculoPrecioService;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String PREFIX = "ZTESTFORMULA_";
    private static final BigDecimal COMISION = new BigDecimal("13");

    private Integer canalId;

    @BeforeEach
    void setUp() {
        // Canal de prueba sin canalBase (requisito de simularPrecioCompleto).
        Canal canal = new Canal();
        canal.setNombre(PREFIX + "CANAL");
        canal = canalRepository.save(canal);
        this.canalId = canal.getId();

        // Conceptos: el canal usa el margen minorista del producto, aplica el IVA del
        // producto y cobra una comisión del 13% sobre el PVP (divisor).
        asignarConcepto(canal, PREFIX + "MARGEN_MIN", AplicaSobre.FLAG_USAR_MARGEN_MINORISTA, BigDecimal.ZERO);
        asignarConcepto(canal, PREFIX + "IVA", AplicaSobre.FLAG_APLICAR_IVA, BigDecimal.ZERO);
        asignarConcepto(canal, PREFIX + "COMISION", AplicaSobre.COMISION_SOBRE_PVP, COMISION);

        entityManager.flush();
    }

    private void asignarConcepto(Canal canal, String nombre, AplicaSobre aplicaSobre, BigDecimal porcentaje) {
        ConceptoCalculo concepto = new ConceptoCalculo();
        concepto.setNombre(nombre);
        concepto.setPorcentaje(porcentaje);
        concepto.setAplicaSobre(aplicaSobre);
        concepto = conceptoCalculoRepository.save(concepto);

        CanalConcepto cc = new CanalConcepto();
        cc.setId(new CanalConceptoId(canal.getId(), concepto.getId()));
        cc.setCanal(canal);
        cc.setConcepto(concepto);
        canalConceptoRepository.save(cc);
    }

    /** Construye un input de simulación variando costo, IVA y margen minorista. */
    private SimulacionPrecioInputDTO input(String costo, String iva, String margenMinorista) {
        return new SimulacionPrecioInputDTO(
                canalId,
                null,                          // cuotas: sin cuotas (contado simple)
                new BigDecimal(costo),
                new BigDecimal(iva),
                null, null, null, null,        // marca, tipo, clasifGral, clasifGastro
                null,                          // tag
                null,                          // proveedorFinanciacionPorcentaje
                null, null,                    // mlaPrecioEnvio, mlaComisionPorcentaje
                new BigDecimal(margenMinorista),
                new BigDecimal("30"),          // margenMayorista (no se usa en este canal)
                null, null                     // precioInflado tipo/valor
        );
    }

    private PrecioCalculadoDTO calcular(String costo, String iva, String margenMinorista) {
        SimulacionResultadoDTO resultado = calculoPrecioService.simularPrecioCompleto(
                input(costo, iva, margenMinorista));
        assertNotNull(resultado, "El resultado de simulación no debe ser null");
        assertNotNull(resultado.indicadores(), "Los indicadores no deben ser null");
        return resultado.indicadores();
    }

    private static void assertMonto(String campo, String esperado, BigDecimal actual) {
        assertNotNull(actual, campo + " no debe ser null");
        assertEquals(0, new BigDecimal(esperado).compareTo(actual),
                () -> campo + ": esperado=" + esperado + " actual=" + actual);
    }

    @Test
    @DisplayName("PVP base: (100 × 1.5 × 1.21) / 0.87 = 208.62")
    void pvpBaseCalculaCorrectamente() {
        PrecioCalculadoDTO r = calcular("100", "21", "50");

        // PVP = (100 × (1 + 50/100) × (1 + 21/100)) / (1 − 13/100) = 181.5 / 0.87 = 208.62
        assertMonto("pvp", "208.62", r.pvp());
        // costoProducto = costo × (1 + financiacionProveedor/100) = 100 × 1 = 100
        assertMonto("costoProducto", "100.00", r.costoProducto());
        assertTrue(r.ganancia().compareTo(BigDecimal.ZERO) > 0,
                "La ganancia debe ser positiva en el escenario base");
    }

    @Test
    @DisplayName("Sin IVA (iva=0) el PVP baja a 150 / 0.87 = 172.41")
    void sinIvaElPvpEsMenor() {
        PrecioCalculadoDTO conIva = calcular("100", "21", "50");
        PrecioCalculadoDTO sinIva = calcular("100", "0", "50");

        // Sin IVA: PVP = (100 × 1.5 × 1.0) / 0.87 = 172.41
        assertMonto("pvp sin IVA", "172.41", sinIva.pvp());
        assertTrue(sinIva.pvp().compareTo(conIva.pvp()) < 0,
                "El PVP sin IVA debe ser menor que el PVP con IVA");
    }

    @Test
    @DisplayName("Margen minorista 0 es rechazado (el motor lo trata como no cargado)")
    void margenCeroEsRechazado() {
        // validarMargenRequerido: margen == null || margen <= 0 -> BadRequestException
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> calcular("100", "21", "0"));
        assertTrue(ex.getMessage().toLowerCase().contains("margen"),
                () -> "El mensaje debe referirse al margen: " + ex.getMessage());
    }

    @Test
    @DisplayName("Mayor margen produce mayor PVP (monotonía)")
    void mayorMargenMayorPvp() {
        PrecioCalculadoDTO margen50 = calcular("100", "21", "50");
        PrecioCalculadoDTO margen80 = calcular("100", "21", "80");

        assertTrue(margen80.pvp().compareTo(margen50.pvp()) > 0,
                "Con margen 80% el PVP debe superar al de margen 50%");
    }

    @Test
    @DisplayName("Costo 0 es rechazado (el motor lo trata como no cargado)")
    void costoCeroEsRechazado() {
        // calcularPrecioUnificado: costo == null || costo == 0 -> BadRequestException
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> calcular("0", "21", "50"));
        assertTrue(ex.getMessage().toLowerCase().contains("costo"),
                () -> "El mensaje debe referirse al costo: " + ex.getMessage());
    }

    @Test
    @DisplayName("Las métricas contables son internamente consistentes")
    void metricasInternamenteConsistentes() {
        PrecioCalculadoDTO r = calcular("100", "21", "50");

        // ganancia = ingresoNetoVendedor − costoProducto (ambos con escala 2)
        BigDecimal gananciaEsperada = r.ingresoNetoVendedor().subtract(r.costoProducto());
        assertEquals(0, gananciaEsperada.compareTo(r.ganancia()),
                () -> "ganancia esperada=" + gananciaEsperada + " actual=" + r.ganancia());

        // ingresoNetoVendedor < pvp (siempre se descuentan IVA y costos de venta)
        assertTrue(r.ingresoNetoVendedor().compareTo(r.pvp()) < 0,
                "El ingreso neto del vendedor debe ser menor que el PVP");

        // costosVenta > 0 (hay comisión del 13%)
        assertTrue(r.costosVenta().compareTo(BigDecimal.ZERO) > 0,
                "Debe haber costos de venta por la comisión");

        // margenSobrePvp = ganancia / pvp × 100 (escala 2)
        BigDecimal margenPvpEsperado = r.ganancia()
                .multiply(new BigDecimal("100"))
                .divide(r.pvp(), 2, RoundingMode.HALF_UP);
        assertEquals(0, margenPvpEsperado.compareTo(r.margenSobrePvp()),
                () -> "margenSobrePvp esperado=" + margenPvpEsperado + " actual=" + r.margenSobrePvp());

        // markupPorcentaje = ganancia / costoProducto × 100 (escala 2)
        BigDecimal markupEsperado = r.ganancia()
                .multiply(new BigDecimal("100"))
                .divide(r.costoProducto(), 2, RoundingMode.HALF_UP);
        assertEquals(0, markupEsperado.compareTo(r.markupPorcentaje()),
                () -> "markupPorcentaje esperado=" + markupEsperado + " actual=" + r.markupPorcentaje());
    }

    @Test
    @DisplayName("Cuota negativa (transferencia con descuento): el PVP FINAL de la fórmula coincide con el indicador PVP")
    void cuotaNegativaFormulaCoincideConIndicador() {
        // Opción de cuota con descuento (transferencia -15%) en el canal de prueba.
        // El motor (indicadores) aplica el descuento; el generador de la fórmula paso a paso
        // debe replicarlo para que su resultado final coincida con el indicador PVP.
        Canal canal = canalRepository.findById(canalId).orElseThrow();
        CanalConceptoCuota cuota = new CanalConceptoCuota();
        cuota.setCanal(canal);
        cuota.setCuotas(-1);
        cuota.setPorcentaje(new BigDecimal("-15"));
        cuota.setDescripcion(PREFIX + "TRANSFERENCIA");
        canalConceptoCuotaRepository.save(cuota);
        entityManager.flush();

        SimulacionPrecioInputDTO in = new SimulacionPrecioInputDTO(
                canalId,
                -1,                            // cuotas: transferencia (descuento)
                new BigDecimal("100"),
                new BigDecimal("21"),
                null, null, null, null,        // marca, tipo, clasifGral, clasifGastro
                null,                          // tag
                null,                          // proveedorFinanciacionPorcentaje
                null, null,                    // mlaPrecioEnvio, mlaComisionPorcentaje
                new BigDecimal("50"),
                new BigDecimal("30"),          // margenMayorista
                null, null                     // precioInflado tipo/valor
        );

        SimulacionResultadoDTO resultado = calculoPrecioService.simularPrecioCompleto(in);

        BigDecimal pvpIndicador = resultado.indicadores().pvp();
        BigDecimal pvpFormula = resultado.formula().resultadoFinal();

        // Sin precio inflado configurado, el resultadoFinal de la fórmula es el PVP, que debe
        // coincidir con el indicador (tolerancia mínima por redondeo de los dos caminos).
        BigDecimal diferencia = pvpFormula.subtract(pvpIndicador).abs();
        assertTrue(diferencia.compareTo(new BigDecimal("0.02")) <= 0,
                () -> "El PVP FINAL de la fórmula (" + pvpFormula + ") debe coincidir con el indicador PVP ("
                        + pvpIndicador + ") cuando la cuota es un descuento (transferencia). Diferencia=" + diferencia);
    }
}
