// Tipos para la vista de "Fórmula del Canal".
// Compone datos de varios endpoints en una estructura jerárquica para visualización.

import type { CanalConceptoReglaDTO, TipoRegla } from "../canal-concepto-regla/types";
import type { CanalReglaDTO } from "../canal-regla/types";

// Las cinco etapas en las que se agrupan los conceptos.
export type EtapaId = "COSTO" | "MARGEN" | "IMPUESTOS" | "PRECIO" | "POST_PRECIO";

export interface EtapaInfo {
    id: EtapaId;
    label: string;
    descripcion: string;
    colorClass: string;       // bg + text para el header de la etapa
    badgeClass: string;       // para badges del concepto
    accentClass: string;      // para borde lateral
    icon: string;             // emoji corto
}

// Un concepto del canal con todas sus reglas asociadas.
export interface ConceptoEnCanal {
    conceptoId: number;
    nombre: string;
    porcentaje: number;
    aplicaSobre: string;
    etapa: EtapaId;
    naturaleza: string; // 'COSTO_VENTA' | 'INFLACION' | etc.
    descripcion: string | null;
    reglas: CanalConceptoReglaDTO[];   // reglas que aplican a este concepto
}

// Etapa con sus conceptos.
export interface EtapaConConceptos {
    info: EtapaInfo;
    conceptos: ConceptoEnCanal[];
}

// Plan de cuotas.
export interface CuotaCanal {
    id: number;
    cuotas: number;
    porcentaje: number;
    descripcion: string;
}

// Vista compuesta completa.
export interface CanalFormulaView {
    canalId: number;
    canalNombre: string;
    canalBaseNombre?: string | null;     // si está seteado, este canal calcula sobre el PVP del padre
    reglasCanal: CanalReglaDTO[];        // eligibilidad de productos
    etapas: EtapaConConceptos[];
    cuotas: CuotaCanal[];
    totalConceptos: number;
    totalReglasExcepcion: number;
}

export type { TipoRegla };
