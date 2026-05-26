package ar.com.leo.super_master_backend.apis.dux.sincronizacion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Horario diario programado para disparar la sincronización con DUX.
 * Una fila por disparo (ej. 06:00 y 18:00 = dos filas). Zona AR.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "dux_horario_sync", schema = "supermaster")
public class DuxHorarioSync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "hora", nullable = false)
    private Integer hora;

    @Column(name = "minuto", nullable = false)
    private Integer minuto;
}
