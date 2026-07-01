package ar.com.leo.super_master_backend.dominio.producto.mla.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mlas", schema = "supermaster")
public class Mla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mla", nullable = false)
    private Integer id;

    @Size(max = 20)
    @NotNull
    @Column(name = "mla", nullable = false, length = 20)
    private String mla;

    @Size(max = 20)
    @Column(name = "mlau", length = 20, unique = true)
    private String mlau;

    // Modelo nuevo de ML (User Products): la familia agrupa las variantes por family_name.
    // family_id se guarda como texto porque puede exceder BIGINT con signo (~2^64).
    @Size(max = 30)
    @Column(name = "family_id", length = 30)
    private String familyId;

    @Size(max = 255)
    @Column(name = "family_name", length = 255)
    private String familyName;

    @Column(name = "precio_envio", precision = 10, scale = 2)
    private BigDecimal precioEnvio;

    @Column(name = "fecha_calculo_envio")
    private LocalDateTime fechaCalculoEnvio;

    @Column(name = "comision_porcentaje", precision = 5, scale = 2)
    private BigDecimal comisionPorcentaje;

    @Column(name = "fecha_calculo_comision")
    private LocalDateTime fechaCalculoComision;

    @NotNull
    @Column(name = "tope_promocion", nullable = false)
    private Integer topePromocion = 0;

    // Relación inversa: un MLA puede estar en varios productos
    @OneToMany(mappedBy = "mla")
    private Set<Producto> productos = new LinkedHashSet<>();

}