package ar.com.leo.super_master_backend.dominio.canal.entity;

import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "canal_concepto", schema = "supermaster")
public class CanalConcepto {

    @EmbeddedId
    private CanalConceptoId id;

    @MapsId("canalId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    @MapsId("conceptoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_concepto", nullable = false)
    private ConceptoCalculo concepto;

}