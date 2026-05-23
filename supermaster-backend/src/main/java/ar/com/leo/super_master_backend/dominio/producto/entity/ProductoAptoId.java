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
public class ProductoAptoId implements Serializable {

    private static final long serialVersionUID = -2162252342941568559L;

    @NotNull
    @Column(name = "id_apto", nullable = false)
    private Integer aptoId;

    @NotNull
    @Column(name = "id_producto", nullable = false)
    private Integer productoId;

    public ProductoAptoId(Integer aptoId, Integer productoId) {
        this.aptoId = aptoId;
        this.productoId = productoId;
    }

}
