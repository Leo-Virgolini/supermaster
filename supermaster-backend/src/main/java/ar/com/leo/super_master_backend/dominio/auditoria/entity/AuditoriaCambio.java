package ar.com.leo.super_master_backend.dominio.auditoria.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "auditoria_cambios", schema = "supermaster")
public class AuditoriaCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_auditoria_cambio", nullable = false)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entidad", nullable = false, length = 40)
    private AuditoriaEntidad entidad;

    // Nullable: hay eventos sin id especifico (login fallido, recalculo masivo,
    // relaciones M:N donde el id es compuesto). Para esos casos se usa entidadCodigo.
    @Column(name = "entidad_id")
    private Integer entidadId;

    @Column(name = "entidad_codigo", length = 150)
    private String entidadCodigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "accion", nullable = false, length = 20)
    private AuditoriaAccion accion;

    @Column(name = "campo", nullable = false, length = 100)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(name = "id_usuario")
    private Integer usuarioId;

    @Column(name = "usuario_username", length = 50)
    private String usuarioUsername;

    @Column(name = "usuario_nombre_completo", length = 150)
    private String usuarioNombreCompleto;

    @Column(name = "origen", nullable = false, length = 50)
    private String origen;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @PrePersist
    protected void onCreate() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
