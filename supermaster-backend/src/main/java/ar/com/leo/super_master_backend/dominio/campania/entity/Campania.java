package ar.com.leo.super_master_backend.dominio.campania.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "campania", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(name = "uq_campania_tn_categoria", columnNames = {"tn_categoria_id"}))
public class Campania {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "tn_categoria_id", nullable = false)
    private Long tnCategoriaId;

    @Size(max = 150)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @ColumnDefault("0")
    @Column(name = "activa", nullable = false)
    private Boolean activa = false;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;

    @Size(max = 255)
    @Column(name = "observaciones", length = 255)
    private String observaciones;

    public Campania(Integer id) {
        this.id = id;
    }
}
