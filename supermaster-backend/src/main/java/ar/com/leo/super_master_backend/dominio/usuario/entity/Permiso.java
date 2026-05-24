package ar.com.leo.super_master_backend.dominio.usuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permisos", schema = "supermaster")
public class Permiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso", nullable = false)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 50, unique = true)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    // Identidad por id. Crucial porque Permiso vive en un Set<Permiso> dentro de Rol:
    // sin equals/hashCode estables, los contains/remove rompen post-merge.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permiso other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
