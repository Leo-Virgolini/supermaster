package ar.com.leo.super_master_backend.dominio.producto.entity;

import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "producto_cliente", schema = "supermaster")
public class ProductoCliente {

    @EmbeddedId
    private ProductoClienteId id;

    // ---------------------------
    // RELACIÓN CON PRODUCTO
    // ---------------------------
    @MapsId("productoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    // ---------------------------
    // RELACIÓN CON CLIENTE
    // ---------------------------
    @MapsId("clienteId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    public ProductoCliente(Producto producto, Cliente cliente) {
        this.producto = producto;
        this.cliente = cliente;
        this.id = new ProductoClienteId(producto.getId(), cliente.getId());
    }

}