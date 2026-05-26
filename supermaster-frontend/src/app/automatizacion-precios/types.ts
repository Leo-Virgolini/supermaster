export interface SincronizacionConfig {
    sellerCampaignPct: number | null;
    dealPct: number | null;
    smartPct: number | null;
    canalMl: string | null;
    cuotasMl: number | null;
    listaPreciosMl: string | null;
    sinIvaMl: boolean | null;
    canalGastro: string | null;
    cuotasGastro: number | null;
    listaPreciosGastro: string | null;
    sinIvaGastro: boolean | null;
    canalHogar: string | null;
    cuotasHogar: number | null;
    listaPreciosHogar: string | null;
    sinIvaHogar: boolean | null;
}

/** Steps booleanos (cada uno representa un checkbox en la UI). */
export interface SyncSteps {
    importarCostosDux: boolean;
    bajarTitulosNube: boolean;
    generarEnvio: boolean;
    excluirPromociones: boolean;
    duxMl: boolean;
    duxGastro: boolean;
    duxNube: boolean;
    preciosMl: boolean;
    incluirPromociones: boolean;
    preciosNube: boolean;
}

export interface SyncRequest extends SyncSteps {
    /** Si tiene valores, restringe la sincronización solo a estos MLAs. */
    filtroMlas?: string[];
}

export interface SincronizacionResult {
    duxImportActualizados: number;
    duxImportTotal: number;
    duxImportErrores: number;
    titulosActualizados: number;
    titulosSinCambio: number;
    titulosSinSku: number;
    envioCalculados: number;
    envioErrores: number;
    excluidosExitosos: number;
    excluidosErrores: number;
    duxMlProductos: number;
    duxMlEstado: string;
    duxGastroProductos: number;
    duxGastroEstado: string;
    duxNubeProductos: number;
    duxNubeEstado: string;
    mlActualizados: number;
    mlErrores: number;
    promoIncluidos: number;
    promoErrores: number;
    nubeActualizados: number;
    nubeErrores: number;
    log: string[];
}
