package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_config", schema = "supermaster")
public class SeoConfig {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "prompt_hogar", nullable = false, columnDefinition = "TEXT")
    private String promptHogar;

    @Column(name = "prompt_gastro", nullable = false, columnDefinition = "TEXT")
    private String promptGastro;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "precio_input_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioInput1m;

    @Column(name = "precio_output_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioOutput1m;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
