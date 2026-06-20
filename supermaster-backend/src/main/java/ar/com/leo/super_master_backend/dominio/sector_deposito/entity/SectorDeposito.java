package ar.com.leo.super_master_backend.dominio.sector_deposito.entity;

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
@Table(name = "sectores_deposito", schema = "supermaster")
public class SectorDeposito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sector_deposito", nullable = false)
    private Integer id;

    @Size(max = 20)
    @NotNull
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "id_dux")
    private Integer idDux;

    public SectorDeposito(Integer id) {
        this.id = id;
    }
}
