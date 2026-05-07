package ar.com.leo.super_master_backend.dominio.reposicion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "venta_diaria_cache", schema = "supermaster",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sku", "fecha"}))
public class VentaDiariaCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sku", nullable = false, length = 45)
    private String sku;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad = 0;

    public VentaDiariaCache(String sku, LocalDate fecha, int cantidad) {
        this.sku = sku;
        this.fecha = fecha;
        this.cantidad = cantidad;
    }
}
