package ar.com.leo.super_master_backend.apis.ml.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "configuracion_ml", schema = "supermaster")
public class ConfiguracionMl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @Column(name = "umbral_envio_gratis", nullable = false, precision = 10, scale = 2)
    private BigDecimal umbralEnvioGratis;

    // Tiers de costo de envío por rangos de PVP
    @Column(name = "tier1_hasta", precision = 12, scale = 2)
    private BigDecimal tier1Hasta;

    @Column(name = "tier1_costo", precision = 10, scale = 2)
    private BigDecimal tier1Costo;

    @Column(name = "tier2_hasta", precision = 12, scale = 2)
    private BigDecimal tier2Hasta;

    @Column(name = "tier2_costo", precision = 10, scale = 2)
    private BigDecimal tier2Costo;

    @Column(name = "tier3_costo", precision = 10, scale = 2)
    private BigDecimal tier3Costo;

}
