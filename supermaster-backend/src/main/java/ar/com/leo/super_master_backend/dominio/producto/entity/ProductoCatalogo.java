package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_catalogo", schema = "supermaster")
public class ProductoCatalogo {

    @EmbeddedId
    private ProductoCatalogoId id;

    // ---------------------------
    // RELACIÓN PRODUCTO
    // ---------------------------
    @MapsId("productoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    // ---------------------------
    // RELACIÓN CATALOGO
    // ---------------------------
    @MapsId("catalogoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_catalogo", nullable = false)
    private Catalogo catalogo;

    public ProductoCatalogo(Producto producto, Catalogo catalogo) {
        this.producto = producto;
        this.catalogo = catalogo;
        this.id = new ProductoCatalogoId(producto.getId(), catalogo.getId());
    }

}