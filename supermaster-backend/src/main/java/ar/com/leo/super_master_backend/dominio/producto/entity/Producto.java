package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "productos", schema = "supermaster")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "sku", nullable = false, length = 45)
    private String sku;

    @Size(max = 45)
    @Column(name = "cod_ext", length = 45)
    private String codExt;

    @Size(max = 100)
    @NotNull
    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;

    @Size(max = 100)
    @Column(name = "titulo_web", nullable = false, length = 100)
    private String tituloWeb;

    @Column(name = "es_combo")
    private Boolean esCombo;

    @Size(max = 500)
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_reposicion")
    private TagReposicion tagReposicion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag")
    private Tag tag;

    // ---------------------------
    // RELACIONES MANY TO ONE
    // ---------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_marca")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_origen")
    private Origen origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clasif_gral")
    private ClasifGral clasifGral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_clasif_gastro")
    private ClasifGastro clasifGastro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo")
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_proveedor")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_material")
    private Material material;

    // ---------------------------
    // ATRIBUTOS NUMÉRICOS Y OTROS
    // ---------------------------

    @Column(name = "uxb")
    private Integer uxb;

    @Column(name = "moq")
    private Integer moq;

    @Size(max = 45)
    @Column(name = "capacidad", length = 45)
    private String capacidad;

    @Column(name = "largo", precision = 10, scale = 2)
    private BigDecimal largo;

    @Column(name = "ancho", precision = 10, scale = 2)
    private BigDecimal ancho;

    @Column(name = "alto", precision = 10, scale = 2)
    private BigDecimal alto;

    @Size(max = 45)
    @Column(name = "diamboca", length = 45)
    private String diamboca;

    @Size(max = 45)
    @Column(name = "diambase", length = 45)
    private String diambase;

    @Size(max = 45)
    @Column(name = "espesor", length = 45)
    private String espesor;

    @Column(name = "costo", precision = 10, scale = 2)
    private BigDecimal costo;

    @Column(name = "fecha_ult_costo", columnDefinition = "datetime DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime fechaUltimoCosto;

    @NotNull
    @Column(name = "iva", nullable = false, precision = 6, scale = 3)
    private BigDecimal iva;

    // ---------------------------
    // RELACIÓN MANY TO ONE CON MLA
    // ---------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_mla")
    private Mla mla;

    // ---------------------------
    // RELACIONES ONE TO MANY
    // ---------------------------

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<ProductoApto> productosApto = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    private Set<ProductoMargen> productoMargenes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    private Set<ProductoCanalPrecio> productoCanalPrecios = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    @BatchSize(size = 50)
    private Set<ProductoCatalogo> productoCatalogos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    @BatchSize(size = 50)
    private Set<ProductoCliente> productoClientes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    private Set<ProductoCanalPrecioInflado> productoCanalPreciosInflados = new LinkedHashSet<>();

    @Column(name = "fecha_creacion", updatable = false, nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion", nullable = true)
    private LocalDateTime fechaModificacion;

    private static final ZoneId ZONA_ARG = ZoneId.of("America/Argentina/Buenos_Aires");

    public Producto(Integer id) {
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
