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

}
