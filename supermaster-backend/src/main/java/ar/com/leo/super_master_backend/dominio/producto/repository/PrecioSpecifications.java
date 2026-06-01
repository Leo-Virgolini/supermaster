package ar.com.leo.super_master_backend.dominio.producto.repository;

import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Specifications para ProductoCanalPrecio.
 * Permite paginar sobre filas de precio (producto+canal+cuota)
 * aplicando filtros sobre atributos del producto via JOIN.
 */
public class PrecioSpecifications {

    /**
     * Obtiene o reutiliza el JOIN a Producto desde ProductoCanalPrecio.
     */
    private static Join<ProductoCanalPrecio, Producto> productoJoin(
            Root<ProductoCanalPrecio> root) {
        // Reutilizar join existente si ya fue creado por otra spec
        for (var join : root.getJoins()) {
            if (join.getAttribute().getName().equals("producto")) {
                @SuppressWarnings("unchecked")
                Join<ProductoCanalPrecio, Producto> existing = (Join<ProductoCanalPrecio, Producto>) join;
                return existing;
            }
        }
        return root.join("producto", JoinType.INNER);
    }

    // =====================================================
    // FILTROS DE PRECIO (campos directos de ProductoCanalPrecio)
    // =====================================================

    public static Specification<ProductoCanalPrecio> canalId(Integer canalId) {
        return (root, query, cb) -> {
            if (canalId == null) return null;
            return cb.equal(root.get("canal").get("id"), canalId);
        };
    }

    public static Specification<ProductoCanalPrecio> cuotas(Integer cuotas) {
        return (root, query, cb) -> {
            if (cuotas == null) return null;
            return cb.equal(root.get("cuotas"), cuotas);
        };
    }

    // =====================================================
    // FILTROS DE PRODUCTO (via JOIN)
    // =====================================================

    public static Specification<ProductoCanalPrecio> productoId(Integer id) {
        return (root, query, cb) -> {
            if (id == null) return null;
            return cb.equal(productoJoin(root).get("id"), id);
        };
    }

    public static Specification<ProductoCanalPrecio> textoLike(String texto) {
        return (root, query, cb) -> {
            if (texto == null || texto.isBlank()) return null;
            String pattern = "%" + texto.toLowerCase() + "%";
            var producto = productoJoin(root);
            var mlaJoin = producto.join("mla", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(producto.get("sku")), pattern),
                    cb.like(cb.lower(producto.get("codExt")), pattern),
                    cb.like(cb.lower(producto.get("descripcion")), pattern),
                    cb.like(cb.lower(producto.get("tituloWeb")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mla")), pattern),
                    cb.like(cb.lower(mlaJoin.get("mlau")), pattern)
            );
        };
    }

    public static Specification<ProductoCanalPrecio> sku(String sku) {
        return (root, query, cb) -> {
            if (sku == null || sku.isBlank()) return null;
            return cb.equal(cb.lower(productoJoin(root).get("sku")), sku.toLowerCase());
        };
    }

    public static Specification<ProductoCanalPrecio> codExt(String codExt) {
        return (root, query, cb) -> {
            if (codExt == null || codExt.isBlank()) return null;
            return cb.equal(cb.lower(productoJoin(root).get("codExt")), codExt.toLowerCase());
        };
    }

    public static Specification<ProductoCanalPrecio> descripcion(String descripcion) {
        return (root, query, cb) -> {
            if (descripcion == null || descripcion.isBlank()) return null;
            return cb.like(cb.lower(productoJoin(root).get("descripcion")), "%" + descripcion.toLowerCase() + "%");
        };
    }

    public static Specification<ProductoCanalPrecio> tituloWeb(String tituloWeb) {
        return (root, query, cb) -> {
            if (tituloWeb == null || tituloWeb.isBlank()) return null;
            return cb.like(cb.lower(productoJoin(root).get("tituloWeb")), "%" + tituloWeb.toLowerCase() + "%");
        };
    }

    public static Specification<ProductoCanalPrecio> esCombo(Boolean esCombo) {
        return (root, query, cb) -> {
            if (esCombo == null) return null;
            return cb.equal(productoJoin(root).get("esCombo"), esCombo);
        };
    }

    public static Specification<ProductoCanalPrecio> uxb(Integer uxb) {
        return (root, query, cb) -> {
            if (uxb == null) return null;
            return cb.equal(productoJoin(root).get("uxb"), uxb);
        };
    }

    public static Specification<ProductoCanalPrecio> esMaquina(Boolean esMaquina) {
        return (root, query, cb) -> {
            if (esMaquina == null) return null;
            var clasifGastroJoin = productoJoin(root).join("clasifGastro", JoinType.LEFT);
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

    public static Specification<ProductoCanalPrecio> tieneMla(Boolean tieneMla) {
        return (root, query, cb) -> {
            if (tieneMla == null) return null;
            return tieneMla
                    ? cb.isNotNull(productoJoin(root).get("mla"))
                    : cb.isNull(productoJoin(root).get("mla"));
        };
    }

    public static Specification<ProductoCanalPrecio> activo(Boolean activo) {
        return (root, query, cb) -> {
            if (activo == null) return null;
            return cb.equal(productoJoin(root).get("activo"), activo);
        };
    }

    public static Specification<ProductoCanalPrecio> tagReposicion(TagReposicion tag) {
        return (root, query, cb) -> {
            if (tag == null) return null;
            return cb.equal(productoJoin(root).get("tagReposicion"), tag);
        };
    }

    public static Specification<ProductoCanalPrecio> tags(List<Tag> tags) {
        return (root, query, cb) -> {
            if (tags == null || tags.isEmpty()) return null;
            return productoJoin(root).get("tag").in(tags);
        };
    }

    // MLA filters
    public static Specification<ProductoCanalPrecio> mla(String mla) {
        return (root, query, cb) -> {
            if (mla == null || mla.isBlank()) return null;
            var mlaJoin = productoJoin(root).join("mla", JoinType.INNER);
            return cb.equal(cb.lower(mlaJoin.get("mla")), mla.toLowerCase());
        };
    }

    public static Specification<ProductoCanalPrecio> mlau(String mlau) {
        return (root, query, cb) -> {
            if (mlau == null || mlau.isBlank()) return null;
            var mlaJoin = productoJoin(root).join("mla", JoinType.INNER);
            return cb.equal(cb.lower(mlaJoin.get("mlau")), mlau.toLowerCase());
        };
    }

    public static Specification<ProductoCanalPrecio> precioEnvioMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(productoJoin(root).join("mla", JoinType.INNER).get("precioEnvio"), min);
        };
    }

    public static Specification<ProductoCanalPrecio> precioEnvioMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(productoJoin(root).join("mla", JoinType.INNER).get("precioEnvio"), max);
        };
    }

    public static Specification<ProductoCanalPrecio> comisionPorcentajeMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(productoJoin(root).join("mla", JoinType.INNER).get("comisionPorcentaje"), min);
        };
    }

    public static Specification<ProductoCanalPrecio> comisionPorcentajeMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(productoJoin(root).join("mla", JoinType.INNER).get("comisionPorcentaje"), max);
        };
    }

    public static Specification<ProductoCanalPrecio> tieneComision(Boolean tieneComision) {
        return (root, query, cb) -> {
            if (tieneComision == null) return null;
            var producto = productoJoin(root);
            var mlaJoin = producto.join("mla", JoinType.LEFT);
            if (tieneComision) {
                return cb.isNotNull(mlaJoin.get("comisionPorcentaje"));
            } else {
                return cb.or(
                        cb.isNull(producto.get("mla")),
                        cb.isNull(mlaJoin.get("comisionPorcentaje"))
                );
            }
        };
    }

    public static Specification<ProductoCanalPrecio> tienePrecioEnvio(Boolean tienePrecioEnvio) {
        return (root, query, cb) -> {
            if (tienePrecioEnvio == null) return null;
            var producto = productoJoin(root);
            var mlaJoin = producto.join("mla", JoinType.LEFT);
            if (tienePrecioEnvio) {
                return cb.isNotNull(mlaJoin.get("precioEnvio"));
            } else {
                return cb.or(
                        cb.isNull(producto.get("mla")),
                        cb.isNull(mlaJoin.get("precioEnvio"))
                );
            }
        };
    }

    // Many-to-One
    public static Specification<ProductoCanalPrecio> marcaIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("marca").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> origenIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("origen").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> tipoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("tipo").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> clasifGralIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("clasifGral").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> clasifGastroIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("clasifGastro").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> proveedorIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("proveedor").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> materialIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).get("material").get("id").in(ids);
        };
    }

    // Rangos
    public static Specification<ProductoCanalPrecio> costoMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(productoJoin(root).get("costo"), min);
        };
    }

    public static Specification<ProductoCanalPrecio> costoMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(productoJoin(root).get("costo"), max);
        };
    }

    public static Specification<ProductoCanalPrecio> ivaMin(BigDecimal min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(productoJoin(root).get("iva"), min);
        };
    }

    public static Specification<ProductoCanalPrecio> ivaMax(BigDecimal max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(productoJoin(root).get("iva"), max);
        };
    }

    public static Specification<ProductoCanalPrecio> stockMin(Integer min) {
        return (root, query, cb) -> {
            if (min == null) return null;
            return cb.ge(productoJoin(root).get("stock"), min);
        };
    }

    public static Specification<ProductoCanalPrecio> stockMax(Integer max) {
        return (root, query, cb) -> {
            if (max == null) return null;
            return cb.le(productoJoin(root).get("stock"), max);
        };
    }

    // Fechas
    public static Specification<ProductoCanalPrecio> desdeFechaUltimoCosto(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.greaterThanOrEqualTo(productoJoin(root).get("fechaUltimoCosto"), f.atStartOfDay());
    }

    public static Specification<ProductoCanalPrecio> hastaFechaUltimoCosto(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.lessThanOrEqualTo(productoJoin(root).get("fechaUltimoCosto"), f.plusDays(1).atStartOfDay());
    }

    public static Specification<ProductoCanalPrecio> desdeFechaCreacion(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.greaterThanOrEqualTo(productoJoin(root).get("fechaCreacion"), f.atStartOfDay());
    }

    public static Specification<ProductoCanalPrecio> hastaFechaCreacion(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.lessThanOrEqualTo(productoJoin(root).get("fechaCreacion"), f.plusDays(1).atStartOfDay());
    }

    public static Specification<ProductoCanalPrecio> desdeFechaModificacion(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.greaterThanOrEqualTo(productoJoin(root).get("fechaModificacion"), f.atStartOfDay());
    }

    public static Specification<ProductoCanalPrecio> hastaFechaModificacion(LocalDate f) {
        return (root, query, cb) -> f == null ? null :
                cb.lessThanOrEqualTo(productoJoin(root).get("fechaModificacion"), f.plusDays(1).atStartOfDay());
    }

    // Many-to-Many (via producto)
    public static Specification<ProductoCanalPrecio> aptoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).join("productosApto").get("apto").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> canalIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return root.get("canal").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> catalogoIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).join("productoCatalogos").get("catalogo").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> clienteIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).join("productoClientes").get("cliente").get("id").in(ids);
        };
    }

    public static Specification<ProductoCanalPrecio> mlaIds(List<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return null;
            return productoJoin(root).join("mla", JoinType.LEFT).get("id").in(ids);
        };
    }

    // Rango PVP (directo sobre ProductoCanalPrecio)
    public static Specification<ProductoCanalPrecio> pvpMin(BigDecimal pvpMin) {
        return (root, query, cb) -> {
            if (pvpMin == null) return null;
            return cb.ge(root.get("pvp"), pvpMin);
        };
    }

    public static Specification<ProductoCanalPrecio> pvpMax(BigDecimal pvpMax) {
        return (root, query, cb) -> {
            if (pvpMax == null) return null;
            return cb.le(root.get("pvp"), pvpMax);
        };
    }
}
