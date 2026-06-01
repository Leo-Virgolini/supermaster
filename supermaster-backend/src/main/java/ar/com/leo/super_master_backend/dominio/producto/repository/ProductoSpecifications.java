package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProductoSpecifications {

    private static void marcarDistinct(CriteriaQuery<?> query) {
        if (query != null) {
            query.distinct(true);
        }
    }

    /* ==========================================================
       0) FILTRO POR ID
       ========================================================== */
    public static Specification<Producto> productoId(Integer productoId) {
        return (root, query, cb) -> {
            if (productoId == null) return null;
            return cb.equal(root.get("id"), productoId);
        };
    }

    /* ==========================================================
       1) BÚSQUEDA POR TEXTO (sku, descripcion, tituloWeb, codExt, mla, mlau)
       ========================================================== */
    public static Specification<Producto> textoLike(String texto) {
        return (root, query, cb) -> {
            if (texto == null || texto.isBlank()) return null;

            String pattern = "%" + texto.toLowerCase() + "%";

            // LEFT JOIN para incluir productos sin MLA
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.LEFT);

            return cb.or(
                    cb.like(cb.lower(root.get("sku")), pattern),
                    cb.like(cb.lower(root.get("codExt")), pattern),
                    cb.like(cb.lower(root.get("descripcion")), pattern),
                    cb.like(cb.lower(root.get("tituloWeb")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mla")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mlau")), pattern)
            );
        };
    }

    /* ==========================================================
       1.1) FILTROS DE TEXTO DEDICADOS (exacto, case insensitive)
       ========================================================== */
    public static Specification<Producto> sku(String sku) {
        return (root, query, cb) -> {
            if (sku == null || sku.isBlank()) return null;
            return cb.equal(cb.lower(root.get("sku")), sku.toLowerCase());
        };
    }

    public static Specification<Producto> codExt(String codExt) {
        return (root, query, cb) -> {
            if (codExt == null || codExt.isBlank()) return null;
            return cb.equal(cb.lower(root.get("codExt")), codExt.toLowerCase());
        };
    }

    public static Specification<Producto> descripcion(String descripcion) {
        return (root, query, cb) -> {
            if (descripcion == null || descripcion.isBlank()) return null;
            return cb.like(cb.lower(root.get("descripcion")), "%" + descripcion.toLowerCase() + "%");
        };
    }

    public static Specification<Producto> tituloWeb(String tituloWeb) {
        return (root, query, cb) -> {
            if (tituloWeb == null || tituloWeb.isBlank()) return null;
            return cb.like(cb.lower(root.get("tituloWeb")), "%" + tituloWeb.toLowerCase() + "%");
        };
    }

    /* ================================
       1.2) FILTRO POR TAG REPOSICIÓN
       ================================ */
    public static Specification<Producto> tagReposicion(TagReposicion tag) {
        return (root, query, cb) -> {
            if (tag == null) return null;
            return cb.equal(root.get("tagReposicion"), tag);
        };
    }

    public static Specification<Producto> tags(List<Tag> tags) {
        return (root, query, cb) -> {
            if (tags == null || tags.isEmpty()) return null;
            return root.get("tag").in(tags);
        };
    }

    /* ================================
       2) FILTROS BOOLEANOS Y NUMÉRICOS
       ================================ */
    public static Specification<Producto> esCombo(Boolean esCombo) {
        return (root, query, cb) -> {
            if (esCombo == null) return null;
            return cb.equal(root.get("esCombo"), esCombo);
        };
    }

    public static Specification<Producto> uxb(Integer uxb) {
        return (root, query, cb) -> {
            if (uxb == null) return null;
            return cb.equal(root.get("uxb"), uxb);
        };
    }

    /* ===========================
       3) FILTROS MANY-TO-ONE (multi-valor)
       =========================== */
    public static Specification<Producto> marcaIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("marca").get("id").in(ids);
        };
    }

    public static Specification<Producto> origenIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("origen").get("id").in(ids);
        };
    }

    public static Specification<Producto> tipoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("tipo").get("id").in(ids);
        };
    }

    public static Specification<Producto> clasifGralIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("clasifGral").get("id").in(ids);
        };
    }

    public static Specification<Producto> clasifGastroIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("clasifGastro").get("id").in(ids);
        };
    }

    public static Specification<Producto> proveedorIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("proveedor").get("id").in(ids);
        };
    }

    public static Specification<Producto> materialIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("material").get("id").in(ids);
        };
    }

    /* ======================
       4) RANGOS: COSTO / IVA
       ====================== */
    public static Specification<Producto> costoMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(root.get("costo"), min);
        };
    }

    public static Specification<Producto> costoMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(root.get("costo"), max);
        };
    }

    public static Specification<Producto> ivaMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(root.get("iva"), min);
        };
    }

    public static Specification<Producto> ivaMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(root.get("iva"), max);
        };
    }

    /* ============================================
       5) RANGO DE FECHAS (fechaUltimoCosto)
       ============================================ */
    public static Specification<Producto> desdeFechaUltimoCosto(LocalDate fecha) {
        return (root, query, cb) -> {
            if (fecha == null) return null;
            return cb.greaterThanOrEqualTo(root.get("fechaUltimoCosto"), fecha.atStartOfDay());
        };
    }

    public static Specification<Producto> hastaFechaUltimoCosto(LocalDate fecha) {
        return (root, query, cb) -> {
            if (fecha == null) return null;
            return cb.lessThanOrEqualTo(root.get("fechaUltimoCosto"), fecha.plusDays(1).atStartOfDay());
        };
    }

    /* ============================================
       6) MANY TO MANY (IN LIST) — al menos uno
       ============================================ */

    public static Specification<Producto> aptoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            marcarDistinct(query);
            return root.join("productosApto").get("apto").get("id").in(ids);
        };
    }

    public static Specification<Producto> catalogoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            marcarDistinct(query);
            return root.join("productoCatalogos").get("catalogo").get("id").in(ids);
        };
    }

    public static Specification<Producto> clienteIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            marcarDistinct(query);
            return root.join("productoClientes").get("cliente").get("id").in(ids);
        };
    }

    public static Specification<Producto> mlaIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.join("mla", JoinType.LEFT).get("id").in(ids);
        };
    }

    /**
     * Filtra productos que tienen precios calculados para un canal y/o cuotas específicos.
     * Usa INNER JOIN para que la paginación SQL sea exacta (no filtra en memoria).
     */
    public static Specification<Producto> tienePreciosEnCanalCuotas(Integer canalId, Integer cuotas) {
        return (root, query, cb) -> {
            // EXISTS subquery: filtra productos que tengan al menos un precio calculado.
            // Usar EXISTS en vez de JOIN evita duplicados y garantiza que el count sea correcto.
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<ProductoCanalPrecio> precioRoot = subquery.from(ProductoCanalPrecio.class);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(precioRoot.get("producto"), root));

            if (canalId != null) {
                predicates.add(cb.equal(precioRoot.get("canal").get("id"), canalId));
            }
            if (cuotas != null) {
                predicates.add(cb.equal(precioRoot.get("cuotas"), cuotas));
            }

            subquery.select(precioRoot.get("id")).where(predicates.toArray(new Predicate[0]));
            return cb.exists(subquery);
        };
    }

    /**
     * Filtra productos que tienen precios calculados en los canales especificados.
     * Usa la relación con ProductoCanalPrecio que tiene id_canal.
     */
    public static Specification<Producto> canalIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            marcarDistinct(query);
            Join<Producto, ?> preciosJoin = root.join("productoCanalPrecios", JoinType.INNER);
            return preciosJoin.get("canal").get("id").in(ids);
        };
    }

    public static Specification<Producto> desdeFechaCreacion(LocalDate f) {
        return (root, query, cb) ->
                f == null ? null :
                        cb.greaterThanOrEqualTo(
                                root.get("fechaCreacion"),
                                f.atStartOfDay()
                        );
    }

    public static Specification<Producto> hastaFechaCreacion(LocalDate f) {
        return (root, query, cb) ->
                f == null ? null :
                        cb.lessThanOrEqualTo(
                                root.get("fechaCreacion"),
                                f.plusDays(1).atStartOfDay()
                        );
    }

    public static Specification<Producto> desdeFechaModificacion(LocalDate f) {
        return (root, query, cb) ->
                f == null ? null :
                        cb.greaterThanOrEqualTo(
                                root.get("fechaModificacion"),
                                f.atStartOfDay()
                        );
    }

    public static Specification<Producto> hastaFechaModificacion(LocalDate f) {
        return (root, query, cb) ->
                f == null ? null :
                        cb.lessThanOrEqualTo(
                                root.get("fechaModificacion"),
                                f.plusDays(1).atStartOfDay()
                        );
    }

    /* ============================================
       7) NUEVOS FILTROS: esMaquina, tieneMla, activo, stock
       ============================================ */

    /**
     * Filtra por esMaquina a través de la relación con ClasifGastro.
     * Si esMaquina es null, no aplica filtro.
     */
    public static Specification<Producto> esMaquina(Boolean esMaquina) {
        return (root, query, cb) -> {
            if (esMaquina == null) return null;
            Join<Producto, ?> clasifGastroJoin = root.join("clasifGastro", JoinType.LEFT);
            if (esMaquina) {
                return cb.equal(clasifGastroJoin.get("esMaquina"), true);
            } else {
                return cb.or(
                        cb.isNull(clasifGastroJoin.get("id")),
                        cb.equal(clasifGastroJoin.get("esMaquina"), false)
                );
            }
        };
    }

    /**
     * Filtra productos que tienen o no tienen MLA asignado.
     * tieneMla=true: solo productos con MLA
     * tieneMla=false: solo productos sin MLA
     */
    public static Specification<Producto> tieneMla(Boolean tieneMla) {
        return (root, query, cb) -> {
            if (tieneMla == null) return null;
            if (tieneMla) {
                return cb.isNotNull(root.get("mla"));
            } else {
                return cb.isNull(root.get("mla"));
            }
        };
    }

    /**
     * Filtra por estado activo/inactivo.
     */
    public static Specification<Producto> activo(Boolean activo) {
        return (root, query, cb) -> {
            if (activo == null) return null;
            return cb.equal(root.get("activo"), activo);
        };
    }

    /**
     * Filtra por stock mínimo.
     */
    public static Specification<Producto> stockMin(Integer min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(root.get("stock"), min);
        };
    }

    /**
     * Filtra por stock máximo.
     */
    public static Specification<Producto> stockMax(Integer max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(root.get("stock"), max);
        };
    }

    /**
     * Filtra por rango de PVP en un canal específico.
     * Requiere canalId para saber en qué canal buscar el precio.
     * Solo considera precios de contado (cuotas = null).
     */
    public static Specification<Producto> pvpEnRango(BigDecimal pvpMin, BigDecimal pvpMax, Integer canalId) {
        return (root, query, cb) -> {
            if (canalId == null || (pvpMin == null && pvpMax == null)) return null;
            marcarDistinct(query);

            Join<Producto, ?> preciosJoin = root.join("productoCanalPrecios", JoinType.INNER);

            ArrayList<Predicate> predicates = new ArrayList<>();

            // Filtrar por canal
            predicates.add(cb.equal(preciosJoin.get("canal").get("id"), canalId));

            // Solo contado (cuotas = null)
            predicates.add(cb.isNull(preciosJoin.get("cuotas")));

            // Rango de PVP
            if (pvpMin != null) {
                predicates.add(cb.ge(preciosJoin.get("pvp"), pvpMin));
            }
            if (pvpMax != null) {
                predicates.add(cb.le(preciosJoin.get("pvp"), pvpMax));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /* ============================================
       8) FILTROS MLA: mla, mlau, precioEnvio, comisionPorcentaje
       ============================================ */

    /**
     * Filtra por código MLA exacto (case insensitive).
     */
    public static Specification<Producto> mla(String mla) {
        return (root, query, cb) -> {
            if (mla == null || mla.isBlank()) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.equal(cb.lower(mlaJoin.get("mla")), mla.toLowerCase());
        };
    }

    /**
     * Filtra por código MLAU exacto (case insensitive).
     */
    public static Specification<Producto> mlau(String mlau) {
        return (root, query, cb) -> {
            if (mlau == null || mlau.isBlank()) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.equal(cb.lower(mlaJoin.get("mlau")), mlau.toLowerCase());
        };
    }

    /**
     * Filtra por precio de envío mínimo.
     */
    public static Specification<Producto> precioEnvioMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.ge(mlaJoin.get("precioEnvio"), min);
        };
    }

    /**
     * Filtra por precio de envío máximo.
     */
    public static Specification<Producto> precioEnvioMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.le(mlaJoin.get("precioEnvio"), max);
        };
    }

    /**
     * Filtra por comisión porcentaje mínima.
     */
    public static Specification<Producto> comisionPorcentajeMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.ge(mlaJoin.get("comisionPorcentaje"), min);
        };
    }

    /**
     * Filtra por comisión porcentaje máxima.
     */
    public static Specification<Producto> comisionPorcentajeMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.INNER);
            return cb.le(mlaJoin.get("comisionPorcentaje"), max);
        };
    }

    /**
     * Filtra productos que tienen o no tienen comisionPorcentaje.
     */
    public static Specification<Producto> tieneComision(Boolean tieneComision) {
        return (root, query, cb) -> {
            if (tieneComision == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.LEFT);
            if (tieneComision) {
                return cb.isNotNull(mlaJoin.get("comisionPorcentaje"));
            } else {
                return cb.or(
                        cb.isNull(root.get("mla")),
                        cb.isNull(mlaJoin.get("comisionPorcentaje"))
                );
            }
        };
    }

    /**
     * Filtra productos que tienen o no tienen precioEnvio.
     */
    public static Specification<Producto> tienePrecioEnvio(Boolean tienePrecioEnvio) {
        return (root, query, cb) -> {
            if (tienePrecioEnvio == null) return null;
            Join<Producto, ?> mlaJoin = root.join("mla", JoinType.LEFT);
            if (tienePrecioEnvio) {
                return cb.isNotNull(mlaJoin.get("precioEnvio"));
            } else {
                return cb.or(
                        cb.isNull(root.get("mla")),
                        cb.isNull(mlaJoin.get("precioEnvio"))
                );
            }
        };
    }

}
