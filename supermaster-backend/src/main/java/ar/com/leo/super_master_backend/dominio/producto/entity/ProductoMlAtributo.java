package ar.com.leo.super_master_backend.dominio.producto.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "producto_ml_atributo", schema = "supermaster")
public class ProductoMlAtributo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "attribute_id", nullable = false, length = 60)
    private String attributeId;

    @Column(name = "value_id", length = 60)
    private String valueId;

    @Column(name = "value_name", nullable = false, length = 255)
    private String valueName;

    /** El usuario marcó este atributo como "No aplica": no se envía a ML. */
    @Column(name = "no_aplica", nullable = false)
    private boolean noAplica;
}
