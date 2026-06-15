package ar.com.leo.super_master_backend.dominio.campania.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@Table(name = "campania_producto", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(name = "uq_campania_producto", columnNames = {"id_campania", "id_producto"}))
public class CampaniaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_campania", nullable = false)
    private Campania campania;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "precio_manual", precision = 15, scale = 2)
    private BigDecimal precioManual;

    @Column(name = "fecha_sync")
    private LocalDateTime fechaSync;

    @Size(max = 255)
    @Column(name = "observaciones", length = 255)
    private String observaciones;
}
