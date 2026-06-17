package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductoFilter(

        // 0) FILTRO POR ID
        Integer productoId,

        // 1) BÚSQUEDA POR TEXTO (parcial en múltiples campos)
        String search,

        // 1.1) FILTROS DE TEXTO DEDICADOS (exacto, case insensitive)
        String sku,
        String codExt,
        String tituloDux,
        String tituloNube,

        // 2) BOOLEANOS / NUMÉRICOS
        Boolean esCombo,
        Integer uxb,
        Boolean esMaquina,
        Boolean tieneMla,
        Boolean activo,
        TagReposicion tagReposicion,
        List<Tag> tags,

        // 2.1) FILTROS MLA
        String mla,                     // código MLA exacto (case insensitive)
        String mlau,                    // código MLAU exacto (case insensitive)
        BigDecimal precioEnvioMin,      // precio envío mínimo
        BigDecimal precioEnvioMax,      // precio envío máximo
        BigDecimal comisionPorcentajeMin, // comisión % mínima
        BigDecimal comisionPorcentajeMax, // comisión % máxima
        Boolean tieneComision,          // tiene comisionPorcentaje != null
        Boolean tienePrecioEnvio,       // tiene precioEnvio != null

        // 3) MANY-TO-ONE (multi-valor)
        List<Integer> marcaIds,
        List<Integer> origenIds,
        List<Integer> tipoIds,
        List<Integer> clasifGralIds,
        List<Integer> clasifGastroIds,
        List<Integer> proveedorIds,
        List<Integer> materialIds,

        // 4) RANGOS (costo / IVA / stock)
        BigDecimal costoMin,
        BigDecimal costoMax,
        BigDecimal ivaMin,
        BigDecimal ivaMax,
        Integer stockMin,
        Integer stockMax,

        // 5) RANGOS PVP (requiere pvpCanalId)
        BigDecimal pvpMin,
        BigDecimal pvpMax,
        Integer pvpCanalId,

        // 6) FECHAS EXISTENTES (fechaUltimoCosto)
        LocalDate desdeFechaUltimoCosto,
        LocalDate hastaFechaUltimoCosto,

        // 7) FECHAS DE CREACIÓN
        LocalDate desdeFechaCreacion,
        LocalDate hastaFechaCreacion,

        // 8) FECHAS DE MODIFICACIÓN
        LocalDate desdeFechaModificacion,
        LocalDate hastaFechaModificacion,

        // 9) MANY-TO-MANY
        List<Integer> aptoIds,
        List<Integer> canalIds,
        List<Integer> catalogoIds,
        List<Integer> clienteIds,
        List<Integer> mlaIds,

        // 10) FILTRAR PRECIOS POR CANAL (también usado para ordenamiento)
        Integer canalId,      // si se especifica, solo devuelve precios de ese canal

        // 11) FILTRAR PRECIOS POR CUOTAS (también usado para ordenamiento)
        Integer cuotas        // si se especifica, solo devuelve precios con esa cantidad de cuotas
) {
}