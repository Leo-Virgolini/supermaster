package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la regla "producto simple completo" invocando el método privado por reflexión
 * (no requiere levantar el contexto Spring).
 */
class ProductoSimpleValidacionTest {

    private void invocar(Producto p) throws Exception {
        ProductoServiceImpl svc = new ProductoServiceImpl(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        Method m = ProductoServiceImpl.class.getDeclaredMethod("validarProductoSimpleCompleto", Producto.class);
        m.setAccessible(true);
        try {
            m.invoke(svc, p);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void simpleSinMarcaRebota() {
        Producto p = new Producto();
        p.setEsCombo(false);
        assertThatThrownBy(() -> invocar(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("marca");
    }

    @Test
    void comboNoExige() throws Exception {
        Producto p = new Producto();
        p.setEsCombo(true);
        invocar(p); // no lanza
    }
}
