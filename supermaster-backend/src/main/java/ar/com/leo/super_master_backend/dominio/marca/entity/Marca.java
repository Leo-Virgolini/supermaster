package ar.com.leo.super_master_backend.dominio.marca.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "marcas", schema = "supermaster")
public class Marca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_marca", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    // -------------------------------------
    // RELACIÓN JERÁRQUICA PADRE → HIJOS
    // -------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_padre")
    private Marca padre;

    @OneToMany(mappedBy = "padre")
    private Set<Marca> subMarcas = new LinkedHashSet<>();

    // -------------------------------------
    // RELACIÓN CON PRODUCTOS
    // -------------------------------------
    @OneToMany(mappedBy = "marca")
    private Set<Producto> productos = new LinkedHashSet<>();

    // -------------------------------------
    // RELACIÓN CON REGLAS DE CANAL-CONCEPTO
    // -------------------------------------
    @OneToMany(mappedBy = "marca")
    private Set<CanalConceptoRegla> canalConceptoReglas = new LinkedHashSet<>();

    public Marca(Integer id) {
        this.id = id;
    }

}