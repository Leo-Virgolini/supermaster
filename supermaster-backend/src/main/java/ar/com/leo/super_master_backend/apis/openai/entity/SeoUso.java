package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_uso", schema = "supermaster")
public class SeoUso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "consultas", nullable = false)
    private long consultas;

    @Column(name = "tokens_entrada", nullable = false)
    private long tokensEntrada;

    @Column(name = "tokens_salida", nullable = false)
    private long tokensSalida;

    @Column(name = "costo_usd", nullable = false, precision = 14, scale = 6)
    private BigDecimal costoUsd;
}
