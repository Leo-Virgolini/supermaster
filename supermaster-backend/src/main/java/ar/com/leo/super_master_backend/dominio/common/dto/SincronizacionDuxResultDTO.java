package ar.com.leo.super_master_backend.dominio.common.dto;

/**
 * Resultado de sincronizar los id_dux de las clasificaciones contra los rubros/subrubros de Dux.
 *
 * @param nivel1       cantidad de clasificaciones de nivel 1 (sin padre) consideradas.
 * @param nivel2       cantidad de clasificaciones de nivel 2 (padre raíz) consideradas.
 * @param actualizados cantidad de clasificaciones cuyo id_dux cambió.
 * @param sinMatch     cantidad de clasificaciones (nivel 1 + nivel 2) sin coincidencia en Dux.
 */
public record SincronizacionDuxResultDTO(int nivel1, int nivel2, int actualizados, int sinMatch) {
}
