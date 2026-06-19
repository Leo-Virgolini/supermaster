package ar.com.leo.super_master_backend.dominio.unidad_medida.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "unidades_medida", schema = "supermaster")
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_unidad_medida", nullable = false)
    private Integer id;

    @Size(max = 20)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "id_dux")
    private Integer idDux;

    public UnidadMedida(Integer id) {
        this.id = id;
    }
}
