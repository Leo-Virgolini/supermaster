package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "imagen_config", schema = "supermaster")
public class ImagenConfig {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "size", nullable = false)
    private String size;

    @Column(name = "output_format", nullable = false)
    private String outputFormat;

    @Column(name = "quality", nullable = false)
    private String quality;

    @Column(name = "precio_input_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioInput1m;

    @Column(name = "precio_output_1m", nullable = false, precision = 12, scale = 4)
    private BigDecimal precioOutput1m;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
