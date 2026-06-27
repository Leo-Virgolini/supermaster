package ar.com.leo.super_master_backend.apis.openai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "imagen_prompt", schema = "supermaster")
public class ImagenPrompt {
    @Id @Column(name = "id")
    private Long id;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;
}
