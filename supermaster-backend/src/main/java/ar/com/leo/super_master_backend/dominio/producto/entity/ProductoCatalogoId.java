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
public class ProductoCatalogoId implements Serializable {

    private static final long serialVersionUID = 8657710790186734706L;

    @NotNull
    @Column(name = "id_producto", nullable = false)
    private Integer productoId;

    @NotNull
    @Column(name = "id_catalogo", nullable = false)
    private Integer catalogoId;

    public ProductoCatalogoId(Integer productoId, Integer catalogoId) {
        this.productoId = productoId;
        this.catalogoId = catalogoId;
    }

}
