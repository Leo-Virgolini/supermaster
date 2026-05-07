package ar.com.leo.super_master_backend.dominio.producto.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_margen", schema = "supermaster",
       uniqueConstraints = @UniqueConstraint(name = "uk_producto", columnNames = {"id_producto"}))
public class ProductoMargen {

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
    // MÁRGENES PORCENTUALES
    // ---------------------------
    @NotNull
    @Column(name = "margen_minorista", nullable = false, precision = 6, scale = 3)
    private BigDecimal margenMinorista;

    @NotNull
    @Column(name = "margen_mayorista", nullable = false, precision = 6, scale = 3)
    private BigDecimal margenMayorista;

    // ---------------------------
    // MÁRGENES FIJOS (en pesos)
    // ---------------------------
    @Column(name = "margen_fijo_minorista", precision = 10, scale = 2)
    private BigDecimal margenFijoMinorista;

    @Column(name = "margen_fijo_mayorista", precision = 10, scale = 2)
    private BigDecimal margenFijoMayorista;

    // ---------------------------
    // OBSERVACIONES
    // ---------------------------
    @Size(max = 300)
    @Column(name = "observaciones", length = 300)
    private String observaciones;

}
