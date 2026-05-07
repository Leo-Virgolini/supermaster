package ar.com.leo.super_master_backend.dominio.config_automatizacion.entity;

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
@Table(name = "config_automatizacion", schema = "supermaster")
public class ConfigAutomatizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 50)
    @NotNull
    @Column(name = "clave", nullable = false, length = 50, unique = true)
    private String clave;

    @Size(max = 100)
    @NotNull
    @Column(name = "valor", nullable = false, length = 100)
    private String valor;

    @Size(max = 255)
    @Column(name = "descripcion", length = 255)
    private String descripcion;

    public ConfigAutomatizacion(Integer id) {
        this.id = id;
    }
}
