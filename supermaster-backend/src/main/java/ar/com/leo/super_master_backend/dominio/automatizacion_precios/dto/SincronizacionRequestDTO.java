package ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto;

public record SincronizacionRequestDTO(
        boolean importarCostosDux,
        boolean generarEnvio,
        boolean excluirPromociones,
        boolean duxMl,
        boolean duxGastro,
        boolean duxNube,
        boolean preciosMl,
        boolean incluirPromociones,
        boolean preciosNube
) {}
