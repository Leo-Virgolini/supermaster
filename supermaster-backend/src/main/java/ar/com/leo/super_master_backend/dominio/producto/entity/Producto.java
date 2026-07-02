package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

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
    @Column(name = "titulo_dux", nullable = false, length = 100)
    private String tituloDux;

    // Título ML NO persistido (fuente de verdad: el ítem de ML). Lo setea el export desde el request
    // antes de publicar; en lote va null y el publish lo omite. Ver plan 2026-07-01-datos-canal-fieles-modal.
    @Transient
    private String tituloMl;

    @Size(max = 100)
    @Column(name = "titulo_nube", length = 100)
    private String tituloNube;

    // Datos de canal NO persistidos (fuente de verdad: el canal). Los setea el export desde el request
    // antes de publicar; en lote van null y el publish los omite. Ver plan 2026-06-29-datos-canal.
    @Transient
    private String descripcionMl;
    @Transient
    private String descripcionNube;
    @Transient
    private String mlCategoryId;
    @Transient
    private java.util.List<ar.com.leo.super_master_backend.apis.ml.dto.MlAtributoDTO> mlAtributos = new java.util.ArrayList<>();
    @Transient
    private boolean equipamientoGastro;
    /** Estado de Dux a enviar en el export (no persistido). "S"/"N"; null = usar `activo`. */
    @Transient
    private String duxHabilitado;
    @Transient
    private String nubePeso;
    @Transient
    private String nubeProfundidad;
    @Transient
    private String nubeAncho;
    @Transient
    private String nubeAlto;
    /** Título override por canal Nube (no persistido). Si es null, el publish usa {@link #tituloNube}. */
    @Transient
    private String tituloNubeCanal;

    @Size(max = 20)
    @Column(name = "ean", length = 20)
    private String ean;

    @Column(name = "es_combo")
    private Boolean esCombo;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sector_deposito")
    private SectorDeposito sectorDeposito;

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

    @Size(max = 45)
    @Column(name = "largo", length = 45)
    private String largo;

    @Size(max = 45)
    @Column(name = "ancho", length = 45)
    private String ancho;

    @Size(max = 45)
    @Column(name = "alto", length = 45)
    private String alto;

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

    @Column(name = "ml_paq_alto", precision = 6, scale = 2)
    private BigDecimal mlPaqAlto;

    @Column(name = "ml_paq_ancho", precision = 6, scale = 2)
    private BigDecimal mlPaqAncho;

    @Column(name = "ml_paq_largo", precision = 6, scale = 2)
    private BigDecimal mlPaqLargo;

    @Column(name = "ml_paq_peso", precision = 8, scale = 3)
    private BigDecimal mlPaqPeso;

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

    // (producto_ml_atributo eliminado; los atributos ML viven en el campo @Transient mlAtributos)

    @OneToMany(mappedBy = "producto")
    private Set<ProductoMargen> productoMargenes = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    private Set<ProductoCanalPrecio> productoCanalPrecios = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    @BatchSize(size = 50)
    private Set<ProductoCatalogo> productoCatalogos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "producto")
    @BatchSize(size = 50)
    private Set<ProductoSegmento> productoSegmentos = new LinkedHashSet<>();

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
