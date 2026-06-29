package ar.com.leo.super_master_backend.apis.nube.service;

import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;

import java.util.List;

/** Reglas de "EQUIPAMIENTO" para KT GASTRO: detección + sufijo de título + bullet de descripción. */
public final class NubeEquipamiento {

    private NubeEquipamiento() {}

    private static final String TEXTO = "ENVIO A COTIZAR";
    private static final String BULLET = "<ul><li>" + TEXTO + "</li></ul>";

    /** True si algún nodo de la categoría de Nube (gastro si existe, sino general) se llama "EQUIPAMIENTO". */
    public static boolean esEquipamiento(Producto p) {
        List<String> nombres = p.getClasifGastro() != null
                ? NubeCategoriaRuta.aplanar(p.getClasifGastro(), ClasifGastro::getPadre, ClasifGastro::getNombre)
                : p.getClasifGral() != null
                    ? NubeCategoriaRuta.aplanar(p.getClasifGral(), ClasifGral::getPadre, ClasifGral::getNombre)
                    : List.of();
        return nombres.stream().anyMatch(n -> n != null && "EQUIPAMIENTO".equalsIgnoreCase(n.trim()));
    }

    /** Agrega "*" pegado al final del título si {@code eq} y no termina ya en "*". */
    public static String tituloConSufijo(String titulo, boolean eq) {
        if (!eq || titulo == null) return titulo;
        return titulo.endsWith("*") ? titulo : titulo + "*";
    }

    /** Agrega el bullet "ENVIO A COTIZAR" al final de la descripción si {@code eq} (idempotente). */
    public static String descripcionConBullet(String desc, boolean eq) {
        if (!eq) return desc;
        if (desc != null && desc.contains(TEXTO)) return desc;
        return (desc == null ? "" : desc) + BULLET;
    }
}
