package ar.com.leo.super_master_backend.dominio.producto.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

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
    // OBSERVACIONES
    // ---------------------------
    @Size(max = 300)
    @Column(name = "observaciones", length = 300)
    private String observaciones;

}
