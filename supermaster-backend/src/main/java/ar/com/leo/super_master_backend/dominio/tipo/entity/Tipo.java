package ar.com.leo.super_master_backend.dominio.tipo.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tipos", schema = "supermaster")
public class Tipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    // -------------------------------------
    // RELACIÓN JERÁRQUICA (PADRE - HIJOS)
    // -------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_padre")
    private Tipo padre;

    @OneToMany(mappedBy = "padre")
    private Set<Tipo> subTipos = new LinkedHashSet<>();

    // -------------------------------------
    // RELACIÓN CON PRODUCTOS
    // -------------------------------------
    @OneToMany(mappedBy = "tipo")
    private Set<Producto> productos = new LinkedHashSet<>();

    // -------------------------------------
    // RELACIÓN CON REGLAS DE CANAL-CONCEPTO
    // -------------------------------------
    @OneToMany(mappedBy = "tipo")
    private Set<CanalConceptoRegla> canalConceptoReglas = new LinkedHashSet<>();

    public Tipo(Integer id) {
        this.id = id;
    }

}