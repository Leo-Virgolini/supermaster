package ar.com.leo.super_master_backend.dominio.canal.entity;

import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "canales", schema = "supermaster")
public class Canal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_canal", nullable = false)
    private Integer id;

    @Size(max = 45)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_canal_base")
    private Canal canalBase;

    @OneToMany(mappedBy = "canalBase")
    private Set<Canal> subcanales = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanalConcepto> canalConceptos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductoCanalPrecio> productoCanalPrecios = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReglaDescuento> reglasDescuentos = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal")
    private Set<CanalConceptoRegla> canalConceptoReglas = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CanalConceptoCuota> canalConceptoCuotas = new LinkedHashSet<>();

    @OneToMany(mappedBy = "canal")
    private Set<ProductoCanalPrecioInflado> productoCanalPreciosInflados = new LinkedHashSet<>();

    // ---------------------------
    // REEVALUACIÓN PENDIENTE (recálculo de catálogo completo contra este canal)
    // Cuando cambia algo que puede agregar/quitar productos del canal
    // (regla, concepto, cuota, descuento, canalBase) este flag se prende.
    // ---------------------------
    @Column(name = "requiere_reevaluar_catalogo", nullable = false)
    private boolean requiereReevaluarCatalogo = false;

    @Column(name = "motivo_reevaluar", length = 255)
    private String motivoReevaluar;

    @Column(name = "marcado_reevaluar_at")
    private LocalDateTime marcadoReevaluarAt;

    public Canal(Integer canalId) {
        this.id = canalId;
    }

}