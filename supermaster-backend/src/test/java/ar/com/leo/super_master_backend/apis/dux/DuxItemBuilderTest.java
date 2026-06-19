package ar.com.leo.super_master_backend.apis.dux;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver;
import ar.com.leo.super_master_backend.apis.dux.service.DuxItemBuilder;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.unidad_medida.entity.UnidadMedida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DuxItemBuilderTest {

    private DuxItemBuilder builder() {
        return new DuxItemBuilder(new DuxClasifResolver());
    }

    private Producto productoCompleto() {
        Producto p = new Producto();
        p.setSku("1234567");
        p.setTituloDux("OLLA ACERO 24CM");
        p.setEsCombo(false);
        p.setActivo(true);
        p.setCosto(new BigDecimal("1000.50"));
        p.setIva(new BigDecimal("21.000"));
        p.setTituloNube("Olla de acero inoxidable 24cm");

        Marca marca = new Marca();
        marca.setCodigoDux("MARCA-1");
        p.setMarca(marca);

        Proveedor prov = new Proveedor();
        prov.setIdDux(77);
        p.setProveedor(prov);

        ClasifGral raiz = new ClasifGral(); raiz.setIdDux(10);
        ClasifGral nivel2 = new ClasifGral(); nivel2.setIdDux(20); nivel2.setPadre(raiz);
        p.setClasifGral(nivel2);

        UnidadMedida um = new UnidadMedida();
        um.setIdDux(18);
        p.setUnidadMedida(um);
        return p;
    }

    @Test
    void construir_mapeaTodosLosCampos() {
        Map<String, Object> m = builder().construir(productoCompleto());

        assertThat(m).containsEntry("cod_item", "1234567");
        assertThat(m).containsEntry("item", "OLLA ACERO 24CM");
        assertThat(m).containsEntry("tipo_producto", "SIMPLE");
        assertThat(m).containsEntry("id_moneda", 1);
        assertThat(m).containsEntry("porc_iva", 21.0);
        assertThat(m).containsEntry("costo", 1000.5);
        assertThat(m).containsEntry("id_proveedor", 77);
        assertThat(m).containsEntry("id_rubro", 10);
        assertThat(m).containsEntry("id_sub_rubro", 20);
        assertThat(m).containsEntry("codigo_marca", "MARCA-1");
        assertThat(m).containsEntry("id_unidad_medida", 18);
        assertThat(m).containsEntry("stockeable", "S");
        assertThat(m).containsEntry("acepta_stock_negativo", "S");
        assertThat(m).containsEntry("habilitado", "S");
        assertThat(m).containsEntry("trazable", "S");
        assertThat(m).containsEntry("disponible_para", "todos");
        assertThat(m).containsEntry("indica_ctd_bultos", "S");
        assertThat(m).containsEntry("descripcion", "Olla de acero inoxidable 24cm");
    }

    @Test
    void construir_noIncluyeCamposViejosNiNulos() {
        Producto p = new Producto();
        p.setSku("9999999");
        p.setTituloDux("X");
        p.setEsCombo(true);
        p.setActivo(false);
        // sin costo, iva, marca, proveedor, clasif, unidad, tituloNube

        Map<String, Object> m = builder().construir(p);

        assertThat(m).containsEntry("cod_item", "9999999");
        assertThat(m).containsEntry("tipo_producto", "COMBO");
        assertThat(m).containsEntry("habilitado", "N");
        // campos eliminados del payload viejo:
        assertThat(m).doesNotContainKey("codigo_externo");
        assertThat(m).doesNotContainKey("ctd_unidades_por_bulto");
        // omitidos por null:
        assertThat(m).doesNotContainKey("costo");
        assertThat(m).doesNotContainKey("porc_iva");
        assertThat(m).doesNotContainKey("id_proveedor");
        assertThat(m).doesNotContainKey("id_rubro");
        assertThat(m).doesNotContainKey("id_sub_rubro");
        assertThat(m).doesNotContainKey("codigo_marca");
        assertThat(m).doesNotContainKey("id_unidad_medida");
        assertThat(m).doesNotContainKey("descripcion");
    }
}
