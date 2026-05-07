package ar.com.leo.super_master_backend.dominio.orden_compra.entity;

import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ordenes_compra", schema = "supermaster")
public class OrdenCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_compra", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoOrdenCompra estado = EstadoOrdenCompra.BORRADOR;

    @Size(max = 500)
    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "ordenCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrdenCompraLinea> lineas = new ArrayList<>();

    @Column(name = "fecha_creacion", updatable = false, nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    private static final ZoneId ZONA_ARG = ZoneId.of("America/Argentina/Buenos_Aires");

    public OrdenCompra(Integer id) {
        this.id = id;
    }

    @PrePersist
    public void prePersist() {
        fechaCreacion = LocalDateTime.now(ZONA_ARG);
    }

    @PreUpdate
    public void preUpdate() {
        fechaModificacion = LocalDateTime.now(ZONA_ARG);
    }
}
