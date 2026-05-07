package ar.com.leo.super_master_backend.dominio.canal.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "canal_concepto_cuota", schema = "supermaster",
       uniqueConstraints = @UniqueConstraint(name = "uk_canal_cuotas", columnNames = {"id_canal", "cuotas"}))
public class CanalConceptoCuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    @NotNull
    @Column(name = "cuotas", nullable = false)
    private Integer cuotas;

    @NotNull
    @Column(name = "porcentaje", nullable = false, precision = 6, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "descripcion", length = 255)
    private String descripcion;
}

