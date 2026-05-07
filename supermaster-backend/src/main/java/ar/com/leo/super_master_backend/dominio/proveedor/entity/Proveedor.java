package ar.com.leo.super_master_backend.dominio.proveedor.entity;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
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
@Table(name = "proveedores", schema = "supermaster")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_proveedor", nullable = false)
    private Integer id;

    @Size(max = 100)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Size(max = 50)
    @NotNull
    @Column(name = "apodo", nullable = false, length = 50)
    private String apodo;

    @Size(max = 45)
    @Column(name = "plazo_pago", length = 45)
    private String plazoPago;

    @Column(name = "entrega")
    private Boolean entrega;

    @Column(name = "financiacion_porcentaje", precision = 6, scale = 3)
    private BigDecimal financiacionPorcentaje;

    @Column(name = "lead_time_dias")
    private Integer leadTimeDias;

    @OneToMany(mappedBy = "proveedor")
    private Set<Producto> productos = new LinkedHashSet<>();

    public Proveedor(Integer id) {
        this.id = id;
    }

}