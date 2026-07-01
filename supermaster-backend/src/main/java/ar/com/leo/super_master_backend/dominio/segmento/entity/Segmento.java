package ar.com.leo.super_master_backend.dominio.segmento.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoSegmento;
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
@Table(name = "segmentos", schema = "supermaster")
public class Segmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_segmento", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    @OneToMany(mappedBy = "segmento", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoSegmento> productoSegmentos = new LinkedHashSet<>();

    public Segmento(Integer segmentoId) {
        this.id = segmentoId;
    }

}
