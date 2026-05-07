// Servicio que compone la vista de "Fórmula del Canal" desde endpoints existentes.

import { API_BASE_URL } from "../config/runtime";
import { fetchAPI } from "../utils/fetchAPI";
import { getConceptosPorCanalAPI } from "../canales/canalConceptosService";
import { getCuotasPorCanalAPI } from "../canal-concepto-cuotas/canalConceptoCuotaService";
import type { CanalConceptoReglaDTO } from "../canal-concepto-regla/types";
import type { CanalReglaDTO } from "../canal-regla/types";
import type { CanalFormulaView, EtapaConConceptos, EtapaInfo, EtapaId } from "./types";
import { ETAPAS_INFO, toEtapaId } from "./etapas";

export interface CanalListItem {
    id: number;
    nombre: string;
    canalBaseId?: number | null;
}

export const getAllCanalesSimpleAPI = async (): Promise<CanalListItem[]> => {
    const res = await fetchAPI(`${API_BASE_URL}/api/canales?page=0&size=500&sort=nombre,asc`);
    if (!res.ok) throw new Error("Error al obtener canales");
    const json = await res.json();
    return (json.content || []).map((c: any) => ({
        id: c.id,
        nombre: c.nombre,
        canalBaseId: c.canalBaseId ?? c.canalBase?.id ?? null,
    }));
};

// Resuelve nombres legibles para los IDs de las reglas (marca, tipo, clasifs).
async function fetchAllSimple(path: string): Promise<{ id: number; nombre: string }[]> {
    try {
        const res = await fetchAPI(`${API_BASE_URL}${path}?page=0&size=500&sort=id,asc`);
        const data = await res.json();
        return (data.content ?? []).map((c: any) => ({ id: c.id, nombre: c.nombre ?? c.descripcion ?? `#${c.id}` }));
    } catch {
        return [];
    }
}

interface LookupMaps {
    marcas: Record<number, string>;
    tipos: Record<number, string>;
    clasifGral: Record<number, string>;
    clasifGastro: Record<number, string>;
}

export const loadLookupMaps = async (): Promise<LookupMaps> => {
    const [marcas, tipos, clasifGral, clasifGastro] = await Promise.all([
        fetchAllSimple("/api/marcas"),
        fetchAllSimple("/api/tipos"),
        fetchAllSimple("/api/clasif-gral"),
        fetchAllSimple("/api/clasif-gastro"),
    ]);
    const toMap = (arr: { id: number; nombre: string }[]): Record<number, string> =>
        arr.reduce((acc, x) => { acc[x.id] = x.nombre; return acc; }, {} as Record<number, string>);
    return {
        marcas: toMap(marcas),
        tipos: toMap(tipos),
        clasifGral: toMap(clasifGral),
        clasifGastro: toMap(clasifGastro),
    };
};

// Trae todas las reglas de excepción de un canal usando el endpoint dedicado del backend.
// El listado paginado de /api/canal-concepto-reglas no acepta filtro por canalId.
async function getAllReglasExcepcionDelCanal(canalId: number): Promise<CanalConceptoReglaDTO[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/canal-concepto-reglas/canal/${canalId}`);
    if (!res.ok) throw new Error("Error al obtener reglas de excepción del canal");
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

async function getAllReglasCanal(canalId: number): Promise<CanalReglaDTO[]> {
    const res = await fetchAPI(`${API_BASE_URL}/api/canal-reglas/canal/${canalId}`);
    if (!res.ok) throw new Error("Error al obtener reglas del canal");
    const data = await res.json();
    return Array.isArray(data) ? data : [];
}

// Compone la vista completa para un canal.
export const buildCanalFormulaView = async (
    canalId: number,
    canalNombre: string,
    canalBaseNombre?: string | null,
): Promise<CanalFormulaView> => {
    const [conceptos, reglasExcepcion, reglasCanal, cuotas] = await Promise.all([
        getConceptosPorCanalAPI(canalId),
        getAllReglasExcepcionDelCanal(canalId),
        getAllReglasCanal(canalId),
        getCuotasPorCanalAPI(canalId).catch(() => []),
    ]);

    // Agrupa reglas de excepción por conceptoId.
    const reglasPorConcepto: Record<number, CanalConceptoReglaDTO[]> = {};
    for (const r of reglasExcepcion) {
        if (!reglasPorConcepto[r.conceptoId]) reglasPorConcepto[r.conceptoId] = [];
        reglasPorConcepto[r.conceptoId].push(r);
    }

    // Agrupa conceptos por etapa.
    const etapasMap: Record<EtapaId, EtapaConConceptos> = {} as Record<EtapaId, EtapaConConceptos>;
    for (const info of ETAPAS_INFO) {
        etapasMap[info.id] = { info, conceptos: [] };
    }

    for (const c of conceptos) {
        const etapaId = toEtapaId(c.etapa);
        etapasMap[etapaId].conceptos.push({
            conceptoId: c.conceptoId,
            nombre: c.nombre,
            porcentaje: c.porcentaje,
            aplicaSobre: c.aplicaSobre,
            etapa: etapaId,
            naturaleza: c.naturaleza,
            descripcion: c.descripcion,
            reglas: reglasPorConcepto[c.conceptoId] || [],
        });
    }

    const etapas = ETAPAS_INFO
        .map((info) => etapasMap[info.id])
        .filter((e) => e.conceptos.length > 0);

    return {
        canalId,
        canalNombre,
        canalBaseNombre: canalBaseNombre ?? null,
        reglasCanal,
        etapas,
        cuotas: cuotas.map((c: any) => ({
            id: c.id,
            cuotas: c.cuotas,
            porcentaje: c.porcentaje,
            descripcion: c.descripcion,
        })),
        totalConceptos: conceptos.length,
        totalReglasExcepcion: reglasExcepcion.length,
    };
};

export type { EtapaInfo };
