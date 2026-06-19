package ar.com.leo.super_master_backend.apis.dux;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver;
import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver.DuxRubro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuxClasifResolverTest {

    private ClasifGral gral(Integer idDux, ClasifGral padre) {
        ClasifGral c = new ClasifGral();
        c.setIdDux(idDux);
        c.setPadre(padre);
        return c;
    }

    private ClasifGastro gastro(Integer idDux, ClasifGastro padre) {
        ClasifGastro c = new ClasifGastro();
        c.setIdDux(idDux);
        c.setPadre(padre);
        return c;
    }

    @Test
    void nodoNivel3_rubroEsRaiz_subrubroEsNivel2() {
        ClasifGral raiz = gral(10, null);
        ClasifGral nivel2 = gral(20, raiz);
        ClasifGral nivel3 = gral(30, nivel2);
        Producto p = new Producto();
        p.setClasifGral(nivel3);

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
        assertThat(r.idSubRubro()).isEqualTo(20);
    }

    @Test
    void nodoNivel1_sinSubrubro() {
        Producto p = new Producto();
        p.setClasifGral(gral(10, null));

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
        assertThat(r.idSubRubro()).isNull();
    }

    @Test
    void ambasClasif_usaGeneral() {
        Producto p = new Producto();
        p.setClasifGral(gral(10, null));
        p.setClasifGastro(gastro(99, null));

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(10);
    }

    @Test
    void soloGastro_usaGastro() {
        ClasifGastro raiz = gastro(99, null);
        ClasifGastro nivel2 = gastro(98, raiz);
        Producto p = new Producto();
        p.setClasifGastro(nivel2);

        DuxRubro r = new DuxClasifResolver().resolver(p);

        assertThat(r.idRubro()).isEqualTo(99);
        assertThat(r.idSubRubro()).isEqualTo(98);
    }

    @Test
    void sinClasif_ambosNull() {
        DuxRubro r = new DuxClasifResolver().resolver(new Producto());
        assertThat(r.idRubro()).isNull();
        assertThat(r.idSubRubro()).isNull();
    }
}
