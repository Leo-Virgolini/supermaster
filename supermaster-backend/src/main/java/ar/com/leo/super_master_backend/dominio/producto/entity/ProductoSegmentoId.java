package ar.com.leo.super_master_backend.dominio.producto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ProductoSegmentoId implements Serializable {

    private static final long serialVersionUID = -5686937608961789449L;

    @NotNull
    @Column(name = "id_producto", nullable = false)
    private Integer productoId;

    @NotNull
    @Column(name = "id_segmento", nullable = false)
    private Integer segmentoId;

    public ProductoSegmentoId(Integer productoId, Integer segmentoId) {
        this.productoId = productoId;
        this.segmentoId = segmentoId;
    }

}
