package ar.com.leo.super_master_backend.dominio.catalogo_pdf_config.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "catalogo_pdf_config", schema = "supermaster")
public class CatalogoPdfConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Size(max = 120)
    @NotNull
    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "canal_id", nullable = false)
    private Integer canalId;

    @Column(name = "catalogo_id", nullable = false)
    private Integer catalogoId;

    @Column(name = "cuotas", nullable = false)
    private Integer cuotas = 0;

    @Size(max = 255)
    @Column(name = "ordenar_por", length = 255)
    private String ordenarPor;

    @Column(name = "clasif_gral_id")
    private Integer clasifGralId;

    @NotNull
    @Column(name = "caratula", nullable = false)
    private Boolean caratula = true;

    @Size(max = 150)
    @Column(name = "titulo", length = 150)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estetica", length = 50)
    private CatalogoPdfEstetica estetica;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 50)
    private CatalogoPdfTipoDocumento tipoDocumento;

    @Column(name = "productos_por_pagina", nullable = false)
    private Integer productosPorPagina = 12;

    @Size(max = 255)
    @Column(name = "ubicacion_salida", length = 255)
    private String ubicacionSalida;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    private static final ZoneId ZONA_ARG = ZoneId.of("America/Argentina/Buenos_Aires");

    @PrePersist
    public void prePersist() {
        fechaModificacion = LocalDateTime.now(ZONA_ARG);
    }

    @PreUpdate
    public void preUpdate() {
        fechaModificacion = LocalDateTime.now(ZONA_ARG);
    }
}
