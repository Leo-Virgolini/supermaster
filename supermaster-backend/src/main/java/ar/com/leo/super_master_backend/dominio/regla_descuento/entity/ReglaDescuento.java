package ar.com.leo.super_master_backend.dominio.regla_descuento.entity;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reglas_descuentos", schema = "supermaster")
public class ReglaDescuento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    // ----------------------------------------
    // RELACIÓN CON CANAL
    // ----------------------------------------
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_canal", nullable = false)
    private Canal canal;

    // ----------------------------------------
    // FILTRO POR CATÁLOGO
    // ----------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_catalogo")
    private Catalogo catalogo;

    // ----------------------------------------
    // FILTRO POR CLASIF GRAL
    // ----------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_clasif_gral")
    private ClasifGral clasifGral;

    // ----------------------------------------
    // FILTRO POR CLASIF GASTRO
    // ----------------------------------------
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "id_clasif_gastro")
    private ClasifGastro clasifGastro;

    // ----------------------------------------
    // CAMPOS NUMÉRICOS
    // ----------------------------------------
    @NotNull
    @Column(name = "monto_minimo", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoMinimo;

    @NotNull
    @Column(name = "descuento_porcentaje", nullable = false, precision = 6, scale = 3)
    private BigDecimal descuentoPorcentaje;

    @ColumnDefault("1")
    @Column(name = "prioridad")
    private Integer prioridad;

    @ColumnDefault("1")
    @Column(name = "activo")
    private Boolean activo;

    @Size(max = 200)
    @Column(name = "descripcion", length = 200)
    private String descripcion;

}