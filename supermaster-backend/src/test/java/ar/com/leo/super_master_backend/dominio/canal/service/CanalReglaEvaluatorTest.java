package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.TipoRegla;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests puros (sin Spring) del evaluador estático
 * {@link CanalReglaServiceImpl#evaluarReglas(List, Producto)}.
 *
 * Contrato:
 *  - Lista vacía/null → el producto aplica.
 *  - Regla sin condiciones → siempre matchea.
 *  - Si hay alguna INCLUIR, el producto debe cumplir al menos una.
 *  - Cualquier EXCLUIR que cumpla deja al producto fuera.
 *  - Condiciones de una regla combinan con AND.
 *  - Tag nulo del producto se trata como MENAJE (fallback).
 */
class CanalReglaEvaluatorTest {

    private Producto productoConTag(Tag tag) {
        Producto p = new Producto();
        p.setId(1);
        p.setTag(tag);
        return p;
    }

    private CanalRegla regla(TipoRegla tipo) {
        CanalRegla r = new CanalRegla();
        Canal canal = new Canal();
        canal.setId(1);
        r.setCanal(canal);
        r.setTipoRegla(tipo);
        return r;
    }

    @Test
    @DisplayName("Lista null → aplica")
    void reglasNull() {
        assertTrue(CanalReglaServiceImpl.evaluarReglas(null, productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("Lista vacía → aplica")
    void reglasVacias() {
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("EXCLUIR sin condiciones → no aplica (regla matchea siempre)")
    void excluirSinCondicionesExcluye() {
        CanalRegla r = regla(TipoRegla.EXCLUIR);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("INCLUIR sin condiciones → aplica (regla matchea siempre)")
    void incluirSinCondicionesIncluye() {
        CanalRegla r = regla(TipoRegla.INCLUIR);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("EXCLUIR tag=MAQUINA excluye producto MAQUINA")
    void excluirPorTagMatchea() {
        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setTag(Tag.MAQUINA);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.MAQUINA)));
    }

    @Test
    @DisplayName("EXCLUIR tag=MAQUINA NO excluye producto MENAJE")
    void excluirPorTagNoMatchea() {
        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setTag(Tag.MAQUINA);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("Tag nulo en producto se evalúa como MENAJE (fallback)")
    void tagNuloFallbackMenaje() {
        CanalRegla excluirMenaje = regla(TipoRegla.EXCLUIR);
        excluirMenaje.setTag(Tag.MENAJE);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(excluirMenaje), productoConTag(null)));

        CanalRegla excluirMaquina = regla(TipoRegla.EXCLUIR);
        excluirMaquina.setTag(Tag.MAQUINA);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(excluirMaquina), productoConTag(null)));
    }

    @Test
    @DisplayName("INCLUIR tag=REPUESTO bloquea producto MENAJE (no cumple ninguna INCLUIR)")
    void incluirExigeCumplirAlMenosUna() {
        CanalRegla r = regla(TipoRegla.INCLUIR);
        r.setTag(Tag.REPUESTO);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("INCLUIR tag=REPUESTO permite producto REPUESTO")
    void incluirCumpleUnaPasa() {
        CanalRegla r = regla(TipoRegla.INCLUIR);
        r.setTag(Tag.REPUESTO);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), productoConTag(Tag.REPUESTO)));
    }

    @Test
    @DisplayName("Dos INCLUIR distintos: basta cumplir una")
    void variasIncluirBastaCumplirUna() {
        CanalRegla r1 = regla(TipoRegla.INCLUIR);
        r1.setTag(Tag.MAQUINA);
        CanalRegla r2 = regla(TipoRegla.INCLUIR);
        r2.setTag(Tag.MENAJE);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r1, r2), productoConTag(Tag.MENAJE)));
    }

    @Test
    @DisplayName("EXCLUIR gana sobre INCLUIR cuando ambos se cumplen")
    void excluirGanaSobreIncluir() {
        CanalRegla incluir = regla(TipoRegla.INCLUIR);
        incluir.setTag(Tag.MAQUINA);
        CanalRegla excluir = regla(TipoRegla.EXCLUIR);
        excluir.setTag(Tag.MAQUINA);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(incluir, excluir), productoConTag(Tag.MAQUINA)));
    }

    @Test
    @DisplayName("Condiciones AND: regla exige tag Y marca — cumple solo si ambas matchean")
    void condicionesCombinadasConAnd() {
        Marca marcaObjetivo = new Marca();
        marcaObjetivo.setId(10);
        Marca otraMarca = new Marca();
        otraMarca.setId(20);

        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setTag(Tag.MAQUINA);
        r.setMarca(marcaObjetivo);

        Producto pCumpleAmbas = productoConTag(Tag.MAQUINA);
        pCumpleAmbas.setMarca(marcaObjetivo);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), pCumpleAmbas),
                "Cumple tag AND marca → excluye");

        Producto pSoloTag = productoConTag(Tag.MAQUINA);
        pSoloTag.setMarca(otraMarca);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), pSoloTag),
                "Cumple tag pero NO marca → no excluye");

        Producto pSoloMarca = productoConTag(Tag.MENAJE);
        pSoloMarca.setMarca(marcaObjetivo);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), pSoloMarca),
                "Cumple marca pero NO tag → no excluye");
    }

    @Test
    @DisplayName("Condición por id_producto: solo matchea al producto específico")
    void condicionPorIdProducto() {
        Producto objetivo = new Producto();
        objetivo.setId(42);

        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setProducto(objetivo);

        Producto pMatch = new Producto();
        pMatch.setId(42);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), pMatch));

        Producto pOther = new Producto();
        pOther.setId(99);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), pOther));
    }

    @Test
    @DisplayName("Condición tieneEnvio=TRUE: matchea solo si precioEnvio > 0")
    void condicionTieneEnvioTrue() {
        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setTieneEnvio(true);

        Producto sinMla = new Producto();
        sinMla.setId(1);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), sinMla),
                "Sin MLA = sin envío → no cumple tieneEnvio=true");

        Producto conEnvioCero = new Producto();
        conEnvioCero.setId(2);
        Mla mlaCero = new Mla();
        mlaCero.setPrecioEnvio(BigDecimal.ZERO);
        conEnvioCero.setMla(mlaCero);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), conEnvioCero),
                "precioEnvio = 0 → no cumple tieneEnvio=true");

        Producto conEnvioPositivo = new Producto();
        conEnvioPositivo.setId(3);
        Mla mlaEnvio = new Mla();
        mlaEnvio.setPrecioEnvio(new BigDecimal("500"));
        conEnvioPositivo.setMla(mlaEnvio);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), conEnvioPositivo),
                "precioEnvio > 0 → cumple tieneEnvio=true → excluye");
    }

    @Test
    @DisplayName("Condición por id_tipo / id_clasif_gral / id_clasif_gastro")
    void condicionPorOtrasRelaciones() {
        Tipo tipoObjetivo = new Tipo();
        tipoObjetivo.setId(5);
        ClasifGral clasifGralObjetivo = new ClasifGral();
        clasifGralObjetivo.setId(7);
        ClasifGastro clasifGastroObjetivo = new ClasifGastro();
        clasifGastroObjetivo.setId(9);

        CanalRegla r = regla(TipoRegla.EXCLUIR);
        r.setTipo(tipoObjetivo);
        r.setClasifGral(clasifGralObjetivo);
        r.setClasifGastro(clasifGastroObjetivo);

        Producto match = new Producto();
        match.setId(1);
        Tipo t = new Tipo();
        t.setId(5);
        match.setTipo(t);
        ClasifGral cg = new ClasifGral();
        cg.setId(7);
        match.setClasifGral(cg);
        ClasifGastro cga = new ClasifGastro();
        cga.setId(9);
        match.setClasifGastro(cga);
        assertFalse(CanalReglaServiceImpl.evaluarReglas(List.of(r), match));

        Producto partial = new Producto();
        partial.setId(2);
        partial.setTipo(t);
        partial.setClasifGral(cg);
        ClasifGastro otra = new ClasifGastro();
        otra.setId(100);
        partial.setClasifGastro(otra);
        assertTrue(CanalReglaServiceImpl.evaluarReglas(List.of(r), partial),
                "Falla una de las condiciones AND → no excluye");
    }
}
