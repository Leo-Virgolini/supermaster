package ar.com.leo.super_master_backend.apis.openai.entity;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "seo_prompt", schema = "supermaster")
public class SeoPrompt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 10)
    private SeoCanal canal;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
