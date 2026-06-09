package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.precio_inflado.entity.PrecioInflado;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_canal_precio_inflado", schema = "supermaster")
public class ProductoCanalPrecioInflado {

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
        // RELACIÓN CON PRECIO INFLADO
        // ---------------------------
        @NotNull
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "id_precio_inflado", nullable = false)
        private PrecioInflado precioInflado;

        // ---------------------------
        // CAMPOS DE LA ENTIDAD
        // ---------------------------
        @ColumnDefault("1")
        @Column(name = "activo", nullable = false)
        private Boolean activo;

        @Column(name = "fecha_desde")
        private LocalDate fechaDesde;

        @Column(name = "fecha_hasta")
        private LocalDate fechaHasta;

        @Size(max = 255)
        @Column(name = "observaciones", length = 255)
        private String observaciones;
}
