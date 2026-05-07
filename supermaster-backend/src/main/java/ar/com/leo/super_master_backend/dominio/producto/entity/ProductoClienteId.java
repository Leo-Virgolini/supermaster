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
public class ProductoClienteId implements Serializable {

    private static final long serialVersionUID = -5686937608961789449L;

    @NotNull
    @Column(name = "id_producto", nullable = false)
    private Integer productoId;

    @NotNull
    @Column(name = "id_cliente", nullable = false)
    private Integer clienteId;

    public ProductoClienteId(Integer productoId, Integer clienteId) {
        this.productoId = productoId;
        this.clienteId = clienteId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductoClienteId)) return false;
        ProductoClienteId that = (ProductoClienteId) o;
        return Objects.equals(productoId, that.productoId) &&
                Objects.equals(clienteId, that.clienteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productoId, clienteId);
    }

}