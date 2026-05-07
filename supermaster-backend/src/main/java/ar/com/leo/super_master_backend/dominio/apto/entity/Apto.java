package ar.com.leo.super_master_backend.dominio.apto.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoApto;
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
@Table(name = "aptos", schema = "supermaster")
public class Apto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_apto", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    // Relación correcta: OneToMany hacia la tabla intermedia
    @OneToMany(mappedBy = "apto", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoApto> productosApto = new LinkedHashSet<>();

    public Apto(Integer aptoId) {
        this.id = aptoId;
    }

}