package ar.com.leo.super_master_backend.apis.dux.service;

import ar.com.leo.super_master_backend.apis.dux.service.DuxClasifResolver.DuxRubro;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Arma el objeto JSON de un producto para POST /item/nuevoItem de Dux. */
@Component
public class DuxItemBuilder {

    private final DuxClasifResolver clasifResolver;

    public DuxItemBuilder(DuxClasifResolver clasifResolver) {
        this.clasifResolver = clasifResolver;
    }

    public Map<String, Object> construir(Producto p) {
        Map<String, Object> item = new HashMap<>();

        // Requeridos / fijos
        boolean esCombo = Boolean.TRUE.equals(p.getEsCombo());
        item.put("cod_item", p.getSku());
        item.put("item", p.getTituloDux() != null ? p.getTituloDux() : "");
        item.put("tipo_producto", esCombo ? "COMBO" : "SIMPLE");
        item.put("id_moneda", 1);
        item.put("habilitado", Boolean.TRUE.equals(p.getActivo()) ? "S" : "N");
        item.put("stockeable", "S");
        item.put("acepta_stock_negativo", "S");
        // Dux rechaza un ítem trazable que además sea compuesto (combo): los combos van NO trazables.
        item.put("trazable", esCombo ? "N" : "S");
        item.put("disponible_para", "TODOS");
        item.put("indica_ctd_bultos", "S");

        // Opcionales (se omiten si null/blank)
        if (p.getIva() != null) item.put("porc_iva", p.getIva().doubleValue());
        // Los combos (compuestos) no llevan costo en Dux: su costo lo deriva de los componentes.
        if (!esCombo && p.getCosto() != null) item.put("costo", p.getCosto().doubleValue());
        if (p.getProveedor() != null && p.getProveedor().getIdDux() != null) {
            item.put("id_proveedor", p.getProveedor().getIdDux());
        }
        if (p.getMarca() != null && p.getMarca().getCodigoDux() != null && !p.getMarca().getCodigoDux().isBlank()) {
            item.put("codigo_marca", p.getMarca().getCodigoDux());
        }
        // La unidad de medida (sector de depósito) NO se manda a Dux: su id no se puede obtener de
        // forma confiable desde la API (Consultar Items no devuelve la unidad), así que un idDux mal
        // cargado haría fallar el alta ("id_unidad_medida no encontrado").
        if (p.getEan() != null && !p.getEan().isBlank()) {
            item.put("cod_barra", p.getEan().trim());
        }
        if (p.getCodExt() != null && !p.getCodExt().isBlank()) {
            item.put("codigo_externo", p.getCodExt().trim());
        }
        if (p.getUxb() != null) {
            item.put("ctd_unidades_por_bulto", p.getUxb());
        }
        if (p.getTituloNube() != null && !p.getTituloNube().isBlank()) {
            item.put("descripcion", p.getTituloNube());
        }

        DuxRubro rubro = clasifResolver.resolver(p);
        if (rubro.idRubro() != null) item.put("id_rubro", rubro.idRubro());
        if (rubro.idSubRubro() != null) item.put("id_sub_rubro", rubro.idSubRubro());

        return item;
    }
}
