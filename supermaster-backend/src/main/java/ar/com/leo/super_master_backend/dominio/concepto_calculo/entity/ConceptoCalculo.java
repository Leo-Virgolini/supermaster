package ar.com.leo.super_master_backend.dominio.concepto_calculo.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConcepto;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "conceptos_calculo", schema = "supermaster")
public class ConceptoCalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_concepto", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    @Column(name = "porcentaje", precision = 6, scale = 3)
    private BigDecimal porcentaje;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "aplica_sobre", nullable = false, columnDefinition = "ENUM('GASTO_SOBRE_COSTO','FLAG_FINANCIACION_PROVEEDOR','AJUSTE_MARGEN_PUNTOS','AJUSTE_MARGEN_PROPORCIONAL','FLAG_USAR_MARGEN_MINORISTA','FLAG_USAR_MARGEN_MAYORISTA','GASTO_POST_GANANCIA','FLAG_APLICAR_IVA','IMPUESTO_EN_FACTOR_IMP','GASTO_POST_IMPUESTOS','FLAG_INCLUIR_ENVIO','COMISION_SOBRE_PVP','FLAG_COMISION_ML','CALCULO_SOBRE_CANAL_BASE','CALCULO_SOBRE_CANAL_BASE_RESELLER','COSTO_OCULTO_PVP','DESCUENTO_PORCENTUAL','INFLACION_DIVISOR_FINAL','GASTO_SIN_INFLAR_PVP','FLAG_APLICAR_PRECIO_INFLADO')")
    private AplicaSobre aplicaSobre;

    /**
     * Naturaleza contable (override). Si es null, se usa el default de aplicaSobre
     * (ver {@link #getNaturalezaResolved()}). Permite que dos conceptos con el mismo
     * aplicaSobre tengan distinto tratamiento en los indicadores (ej: un GASTO_POST_GANANCIA
     * que es plata real vs uno que es solo inflación).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "naturaleza", columnDefinition = "ENUM('COSTO_PRODUCTO','COSTO_VENTA','IMPUESTO','MARKUP','INFLACION','DESCUENTO','BASE','COSMETICO')")
    private NaturalezaConcepto naturaleza;

    @Size(max = 255)
    @Column(name = "descripcion")
    private String descripcion;

    // ----------------------------------------
    // RELACIÓN CON CANALES
    // ----------------------------------------
    // Los conceptos se asocian a canales a través de la tabla canal_concepto
    // Un concepto puede estar asociado a múltiples canales

    @OneToMany(mappedBy = "concepto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanalConcepto> canalConceptos = new LinkedHashSet<>();

    // ----------------------------------------
    // RELACIÓN CON REGLAS DE CANAL-CONCEPTO
    // ----------------------------------------
    @OneToMany(mappedBy = "concepto")
    private Set<CanalConceptoRegla> canalConceptoReglas = new LinkedHashSet<>();

    public ConceptoCalculo(Integer id) {
        this.id = id;
    }

    /**
     * @return la naturaleza efectiva del concepto: el override (columna {@code naturaleza})
     *         si está seteado, o el default del {@code aplicaSobre} si está null.
     *         Esta es la naturaleza que debe consultar el cálculo de indicadores.
     */
    public NaturalezaConcepto getNaturalezaResolved() {
        if (naturaleza != null) {
            return naturaleza;
        }
        return aplicaSobre != null ? aplicaSobre.getNaturalezaDefault() : null;
    }

}
