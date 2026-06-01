package ar.com.leo.super_master_backend.dominio.canal.entity;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "canal_concepto_regla", schema = "supermaster")
public class CanalConceptoRegla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    // =========================
    // RELACIÓN PRINCIPAL
    // =========================

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_concepto", nullable = false)
    private ConceptoCalculo concepto;

    // =========================
    // TIPO DE REGLA
    // =========================

    @ColumnDefault("'EXCLUIR'")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_regla", columnDefinition = "ENUM('INCLUIR','EXCLUIR') NOT NULL DEFAULT 'EXCLUIR'", nullable = false)
    private TipoRegla tipoRegla;

    // =========================
    // CONDICIONES (OPCIONALES)
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_tipo")
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_clasif_gastro")
    private ClasifGastro clasifGastro;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_clasif_gral")
    private ClasifGral clasifGral;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_marca")
    private Marca marca;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    private Tag tag;

    @Column(name = "tiene_envio")
    private Boolean tieneEnvio;

}

