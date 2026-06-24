package ar.com.leo.super_master_backend.dominio.producto.calculo.service;

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
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.tipo.repository.TipoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el override de comisión ML en {@link CalculoPrecioService#calcularPrecioCanalConEnvio}
 * (sobrecarga de 5 parámetros). Usa un producto SIN MLA para garantizar que sin override
 * la comisión es cero, y con override entra al divisor del PVP.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalculoPrecioComisionOverrideTest {

    @Autowired
    private CalculoPrecioService service;

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

    private static final String PREFIX = "ZTESTCOMISION_";

    private Integer productoId;
    private Integer canalId;

    @BeforeEach
    void setUp() {
        // Entidades base requeridas por Producto
        Origen origen = origenRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Origen o = new Origen();
                    o.setNombre(PREFIX + "Origen");
                    return origenRepository.save(o);
                });

        Tipo tipo = tipoRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    Tipo t = new Tipo();
                    t.setNombre(PREFIX + "Tipo");
                    return tipoRepository.save(t);
                });

        ClasifGral clasifGral = clasifGralRepository.findAll().stream().findFirst()
                .orElseGet(() -> {
                    ClasifGral c = new ClasifGral();
                    c.setNombre(PREFIX + "ClasifGral");
                    return clasifGralRepository.save(c);
                });

        // Canal con FLAG_COMISION_ML y FLAG_USAR_MARGEN_MINORISTA (sin IVA para simplificar)
        Canal canal = new Canal();
        canal.setNombre(PREFIX + "ML");
        canal = canalRepository.save(canal);
        canalId = canal.getId();

        // Concepto FLAG_USAR_MARGEN_MINORISTA (requerido para que el motor tome el margen)
        ConceptoCalculo conceptoMargen = new ConceptoCalculo();
        conceptoMargen.setNombre(PREFIX + "MARGEN_MIN");
        conceptoMargen.setPorcentaje(BigDecimal.ZERO);
        conceptoMargen.setAplicaSobre(AplicaSobre.FLAG_USAR_MARGEN_MINORISTA);
        conceptoMargen = conceptoCalculoRepository.save(conceptoMargen);

        // Concepto FLAG_COMISION_ML (el motor usará el override en lugar del MLA)
        ConceptoCalculo conceptoComisionMl = new ConceptoCalculo();
        conceptoComisionMl.setNombre(PREFIX + "FLAG_COMISION_ML");
        conceptoComisionMl.setPorcentaje(BigDecimal.ZERO); // el % viene del MLA o del override
        conceptoComisionMl.setAplicaSobre(AplicaSobre.FLAG_COMISION_ML);
        conceptoComisionMl = conceptoCalculoRepository.save(conceptoComisionMl);

        // Asignar conceptos al canal
        asignarConceptoACanal(canal, conceptoMargen);
        asignarConceptoACanal(canal, conceptoComisionMl);

        // Producto SIN MLA (mla = null) — garantiza que sin override la comisión es cero
        Producto producto = new Producto();
        producto.setSku(PREFIX + "001");
        producto.setTituloDux(PREFIX + "Producto Sin MLA");
        producto.setTituloNube(PREFIX + "Producto Sin MLA Test");
        producto.setCosto(new BigDecimal("10000"));
        producto.setIva(new BigDecimal("0")); // sin IVA para simplificar la aritmética
        producto.setOrigen(origen);
        producto.setTipo(tipo);
        producto.setClasifGral(clasifGral);
        // mla NO se asigna → producto.getMla() == null
        producto = productoRepository.save(producto);
        productoId = producto.getId();

        // Margen minorista 50%
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

    @Test
    void override_comision_aumenta_el_pvp_respecto_de_sin_comision() {
        // Sin override: producto sin MLA → comisión = 0 → PVP = costo * (1 + margen/100)
        PrecioCalculadoDTO sin = service.calcularPrecioCanalConEnvio(
                productoId, canalId, 0, BigDecimal.ZERO, null);

        // Con override 13%: el motor usa 13% como divisor → PVP mayor
        PrecioCalculadoDTO con = service.calcularPrecioCanalConEnvio(
                productoId, canalId, 0, BigDecimal.ZERO, new BigDecimal("13"));

        assertThat(con.pvp()).isGreaterThan(sin.pvp());
    }
}
