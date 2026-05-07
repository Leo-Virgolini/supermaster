package ar.com.leo.super_master_backend.dominio.catalogo.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCatalogo;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "catalogos", schema = "supermaster")
public class Catalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_catalogo", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    @Column(name = "exportar_con_iva", nullable = false)
    private Boolean exportarConIva = true;

    @Column(name = "recargo_porcentaje", nullable = false, precision = 6, scale = 3)
    private BigDecimal recargoPorcentaje = BigDecimal.ZERO;

    // --------------------------
    // RELACIÓN CON PRODUCTOS
    // --------------------------
    @OneToMany(mappedBy = "catalogo", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoCatalogo> productoCatalogos = new LinkedHashSet<>();

    // --------------------------
    // RELACIÓN CON REGLAS
    // --------------------------
    @OneToMany(mappedBy = "catalogo", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReglaDescuento> reglasDescuentos = new LinkedHashSet<>();

    public Catalogo(Integer catalogoId) {
        this.id = catalogoId;
    }

}