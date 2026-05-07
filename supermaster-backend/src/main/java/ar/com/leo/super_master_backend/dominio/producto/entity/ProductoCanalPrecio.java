package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_canal_precios", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(name = "uk_producto_canal_cuotas",
                columnNames = {"id_producto", "id_canal", "cuotas"}))
public class ProductoCanalPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    // ---------------------------
    // RELACIÓN CON PRODUCTO
    // ---------------------------
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    // ---------------------------
    // RELACIÓN CON CANAL
    // ---------------------------
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    // ---------------------------
    // CUOTAS (NULL = contado)
    // ---------------------------
    @Column(name = "cuotas")
    private Integer cuotas;

    // ---------------------------
    // CAMPOS DE LA ENTIDAD
    // ---------------------------
    @Column(name = "pvp", precision = 12, scale = 2)
    private BigDecimal pvp;

    @Column(name = "pvp_inflado", precision = 12, scale = 2)
    private BigDecimal pvpInflado;

    /**
     * Costo del producto = costoBase × (1 + financiacionProveedor/100) × (1 + embalaje/100)
     */
    @Column(name = "costo_producto", precision = 12, scale = 2)
    private BigDecimal costoProducto;

    /**
     * Costos de venta = Σ conceptos donde AplicaSobre IN (COMISION_SOBRE_PVP, DESCUENTO_PORCENTUAL, RECARGO_CUPON, FLAG_INCLUIR_ENVIO)
     * EXCEPTO "EMBALAJE" + porcentajeCuotas
     */
    @Column(name = "costos_venta", precision = 12, scale = 2)
    private BigDecimal costosVenta;

    /**
     * Ingreso neto vendedor = PVP - montoIVA - montoImpuestos - costosVenta
     */
    @Column(name = "ingreso_neto_vendedor", precision = 12, scale = 2)
    private BigDecimal ingresoNetoVendedor;

    /**
     * Ganancia = ingresoNetoVendedor - costoProducto
     */
    @Column(name = "ganancia", precision = 12, scale = 2)
    private BigDecimal ganancia;

    /**
     * Margen sobre ingreso neto = (ganancia / ingresoNetoVendedor) × 100
     * Muestra qué % del ingreso neto es ganancia (rentabilidad real después de gastos)
     */
    @Column(name = "margen_sobre_ingreso_neto", precision = 6, scale = 2)
    private BigDecimal margenSobreIngresoNeto;

    /**
     * Margen sobre PVP = (ganancia / pvp) × 100
     * Muestra qué % del precio de venta es ganancia (comparable con mercado)
     */
    @Column(name = "margen_sobre_pvp", precision = 6, scale = 2)
    private BigDecimal margenSobrePvp;

    /**
     * Markup porcentaje = (ganancia / costoProducto) × 100
     */
    @Column(name = "markup_porcentaje", precision = 6, scale = 2)
    private BigDecimal markupPorcentaje;

    @Column(name = "fecha_ultimo_calculo")
    private LocalDateTime fechaUltimoCalculo;

    // ---------------------------
    // RELACIÓN CON PRECIO INFLADO (solo lectura, para sorting)
    // ---------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "id_producto", referencedColumnName = "id_producto", insertable = false, updatable = false),
            @JoinColumn(name = "id_canal", referencedColumnName = "id_canal", insertable = false, updatable = false)
    })
    private ProductoCanalPrecioInflado precioInfladoAsignacion;

    @PrePersist
    @PreUpdate
    public void actualizarFechaCalculo() {
        fechaUltimoCalculo = LocalDateTime.now();
    }

}