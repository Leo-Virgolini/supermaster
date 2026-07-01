package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.segmento.entity.Segmento;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_segmento", schema = "supermaster")
public class ProductoSegmento {

    @EmbeddedId
    private ProductoSegmentoId id;

    // ---------------------------
    // RELACIÓN CON PRODUCTO
    // ---------------------------
    @MapsId("productoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    // ---------------------------
    // RELACIÓN CON SEGMENTO
    // ---------------------------
    @MapsId("segmentoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_segmento", nullable = false)
    private Segmento segmento;

    public ProductoSegmento(Producto producto, Segmento segmento) {
        this.producto = producto;
        this.segmento = segmento;
        this.id = new ProductoSegmentoId(producto.getId(), segmento.getId());
    }

}
