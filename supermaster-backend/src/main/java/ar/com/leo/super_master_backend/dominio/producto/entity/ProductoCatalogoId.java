package ar.com.leo.super_master_backend.dominio.producto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductoCatalogoId)) return false;
        ProductoCatalogoId that = (ProductoCatalogoId) o;
        return Objects.equals(productoId, that.productoId) &&
                Objects.equals(catalogoId, that.catalogoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productoId, catalogoId);
    }

}