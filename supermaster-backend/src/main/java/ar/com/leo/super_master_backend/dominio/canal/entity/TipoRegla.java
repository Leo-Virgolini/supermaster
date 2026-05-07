package ar.com.leo.super_master_backend.dominio.canal.entity;

/**
 * Enum que define el tipo de regla para CanalConceptoRegla.
 * 
 * - INCLUIR: El concepto SOLO aplica si cumple la condición
 * - EXCLUIR: El concepto NO aplica si cumple la condición
 */
public enum TipoRegla {
    INCLUIR,
    EXCLUIR
}

