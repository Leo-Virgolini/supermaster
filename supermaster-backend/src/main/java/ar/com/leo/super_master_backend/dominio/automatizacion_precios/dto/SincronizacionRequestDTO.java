package ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto;

import java.util.Set;

/**
 * @param bajarTitulosNube si true, antes de calcular precios baja los títulos web
 *                         de KT Gastro desde Tienda Nube y los persiste en `Producto.tituloNube`
 *                         (sólo se actualizan productos cuyo título cambió).
 * @param filtroMlas       si no es null ni vacío, restringe la sincronización solo a estos MLAs.
 *                         Útil para probar contra un subconjunto antes de una corrida completa.
 */
public record SincronizacionRequestDTO(
        boolean importarCostosDux,
        boolean bajarTitulosNube,
        boolean generarEnvio,
        boolean excluirPromociones,
        boolean duxMl,
        boolean duxGastro,
        boolean duxNube,
        boolean preciosMl,
        boolean incluirPromociones,
        boolean preciosNube,
        Set<String> filtroMlas
) {
    // Compatibilidad: llamadas sin filtro / sin paso de títulos reciben defaults.
    public SincronizacionRequestDTO(
            boolean importarCostosDux, boolean generarEnvio, boolean excluirPromociones,
            boolean duxMl, boolean duxGastro, boolean duxNube,
            boolean preciosMl, boolean incluirPromociones, boolean preciosNube
    ) {
        this(importarCostosDux, false, generarEnvio, excluirPromociones,
                duxMl, duxGastro, duxNube,
                preciosMl, incluirPromociones, preciosNube, null);
    }
}
