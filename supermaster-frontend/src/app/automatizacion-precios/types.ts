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

export interface SyncRequest {
    importarCostosDux: boolean;
    generarEnvio: boolean;
    excluirPromociones: boolean;
    duxMl: boolean;
    duxGastro: boolean;
    duxNube: boolean;
    preciosMl: boolean;
    incluirPromociones: boolean;
    preciosNube: boolean;
}

export interface SincronizacionResult {
    duxImportActualizados: number;
    duxImportTotal: number;
    duxImportErrores: number;
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
