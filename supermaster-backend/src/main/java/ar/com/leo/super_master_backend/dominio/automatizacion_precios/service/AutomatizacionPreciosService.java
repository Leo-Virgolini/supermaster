package ar.com.leo.super_master_backend.dominio.automatizacion_precios.service;

import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.dux.dto.ImportDuxResultDTO;
import ar.com.leo.super_master_backend.apis.ml.dto.CostoEnvioResponseDTO;
import ar.com.leo.super_master_backend.apis.ml.service.MercadoLibreService;
import ar.com.leo.super_master_backend.apis.nube.service.TiendaNubeService;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionConfigDTO;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionRequestDTO;
import ar.com.leo.super_master_backend.dominio.automatizacion_precios.dto.SincronizacionResultDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.entity.ConfigAutomatizacion;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.repository.ConfigAutomatizacionRepository;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.producto.mla.repository.MlaRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomatizacionPreciosService {

    private final MercadoLibreService mlService;
    private final DuxService duxService;
    private final TiendaNubeService nubeService;
    private final ConfigAutomatizacionRepository configRepo;
    private final ProductoCanalPrecioRepository precioRepo;
    private final ProductoRepository productoRepository;
    private final CanalRepository canalRepository;
    private final MlaRepository mlaRepository;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor taskExecutor;
    private final ProcesoGlobalService procesoGlobal;

    @Lazy
    @Autowired
    private AutomatizacionPreciosService self;

    private static final String PROCESO_ID = "automatizacion-precios";
    private static final String PROCESO_DESC = "Automatización de Precios";

    // Encapsula estado + locks + supplier de progreso al SSE.
    private EstadoProcesoMasivo tracker;

    @PostConstruct
    void initTracker() {
        this.tracker = new EstadoProcesoMasivo(PROCESO_ID, PROCESO_DESC, procesoGlobal);
    }

    // Resultado del último proceso (consultable post-finalización).
    private volatile SincronizacionResultDTO resultado = null;

    // Log en tiempo real (thread-safe)
    private final CopyOnWriteArrayList<String> logEntries = new CopyOnWriteArrayList<>();


    // ==================== API PÚBLICA ====================

    public boolean iniciar(SincronizacionRequestDTO request) {
        if (!tracker.adquirir()) return false;
        resultado = null;
        logEntries.clear();
        self.ejecutarAsync(request);
        return true;
    }

    public ProcesoMasivoEstadoDTO obtenerEstado() {
        return tracker.obtener();
    }

    public void cancelar() {
        tracker.cancelar();
    }

    public SincronizacionResultDTO obtenerResultado() {
        return resultado;
    }

    /**
     * Retorna los logs acumulados hasta el momento (incluso durante ejecución).
     * Soporta offset para que el frontend solo pida las líneas nuevas.
     */
    public List<String> obtenerLog(int desde) {
        if (desde >= logEntries.size()) return List.of();
        return List.copyOf(logEntries.subList(desde, logEntries.size()));
    }

    public SincronizacionConfigDTO obtenerConfig() {
        return new SincronizacionConfigDTO(
                getConfigInt("seller_campaign_pct"),
                getConfigInt("deal_pct"),
                getConfigInt("smart_pct"),
                getConfigStr("canal_ml"),
                getConfigInt("cuotas_ml"),
                getConfigStr("lista_precios_ml"),
                getConfigBool("sin_iva_ml"),
                getConfigStr("canal_gastro"),
                getConfigInt("cuotas_gastro"),
                getConfigStr("lista_precios_gastro"),
                getConfigBool("sin_iva_gastro"),
                getConfigStr("canal_hogar"),
                getConfigInt("cuotas_hogar"),
                getConfigStr("lista_precios_hogar"),
                getConfigBool("sin_iva_hogar")
        );
    }

    // ==================== EJECUCIÓN ASYNC ====================

    @Async
    public void ejecutarAsync(SincronizacionRequestDTO request) {
        int duxImportActualizados = 0, duxImportTotal = 0, duxImportErrores = 0;
        int titulosActualizados = 0, titulosSinCambio = 0, titulosSinSku = 0;
        int envioOk = 0, envioErr = 0;
        int excluidosOk = 0, excluidosErr = 0;
        int duxMlProductos = 0, duxGastroProductos = 0, duxNubeProductos = 0;
        String duxMlEstado = "-", duxGastroEstado = "-", duxNubeEstado = "-";
        int mlOk = 0, mlErr = 0;
        int promoOk = 0, promoErr = 0;
        int nubeOk = 0, nubeErr = 0;

        try {
            // Cargar configuración
            SincronizacionConfigDTO config = obtenerConfig();
            addLog("Configuración cargada");
            actualizarEstado("Configuración cargada", 0, 0);

            // Determinar si necesitamos PVP ML
            boolean necesitaML = request.excluirPromociones() || request.duxMl()
                    || request.preciosMl() || request.incluirPromociones();

            // Normalizar filtro de MLAs (opcional): si viene, todos los pasos solo operan sobre ese subconjunto.
            Set<String> filtroMlas = (request.filtroMlas() == null || request.filtroMlas().isEmpty())
                    ? null
                    : Set.copyOf(request.filtroMlas());
            if (filtroMlas != null) {
                addLog("Filtro MLA activo: " + filtroMlas.size() + " MLAs seleccionados");
            }

            // Cargar precios ML si se necesitan
            List<ProductoCanalPrecio> preciosML = List.of();
            Map<String, PrecioProductoInfo> mapaMLporMla = Map.of();
            // MLAs en under_review: ML rechaza incluirlos/excluirlos de promos (400),
            // pero sí acepta actualizar precio. Los trackeamos para saltearlos en los pasos de promo.
            Set<String> mlasUnderReview = new HashSet<>();
            if (necesitaML && config.canalMl() != null) {
                preciosML = cargarPrecios(config.canalMl(), config.cuotasMl());
                mapaMLporMla = construirMapaPorMla(preciosML);
                if (filtroMlas != null) {
                    LinkedHashMap<String, PrecioProductoInfo> filtrado = new LinkedHashMap<>(mapaMLporMla);
                    filtrado.keySet().retainAll(filtroMlas);
                    // Reportar los MLAs del filtro que no se encontraron en la lista de precios cargada.
                    Set<String> sinMatch = new HashSet<>(filtroMlas);
                    sinMatch.removeAll(filtrado.keySet());
                    if (!sinMatch.isEmpty()) {
                        String muestra = sinMatch.stream().limit(10).collect(Collectors.joining(", "));
                        String sufijo = sinMatch.size() > 10 ? " (y " + (sinMatch.size() - 10) + " más)" : "";
                        addLogWarn("Filtro: " + sinMatch.size() + " MLAs no se encontraron en el canal ML: " + muestra + sufijo);
                    }
                    mapaMLporMla = filtrado;
                }
                addLog("Precios ML cargados: " + preciosML.size() + " productos, " + mapaMLporMla.size() + " MLAs");
                actualizarEstado("Precios ML cargados", 0, mapaMLporMla.size());

                // Filtrar items no activos (multiget directo sobre listaML, sin scan completo)
                if (!mapaMLporMla.isEmpty()) {
                    actualizarEstado("Verificando estado de " + mapaMLporMla.size() + " items...", 0, mapaMLporMla.size());
                    List<String> mlasListaML = new ArrayList<>(mapaMLporMla.keySet());
                    Map<String, String> statusMap = mlService.obtenerStatusItems(mlasListaML);

                    List<String> mlasAOmitir = new ArrayList<>();
                    for (String mla : mlasListaML) {
                        String status = statusMap.getOrDefault(mla, "desconocido");
                        if ("active".equals(status)) {
                            continue;
                        }
                        if ("paused".equals(status)) {
                            log.info("ML - Item {} pausado, se procesará igualmente", mla);
                            continue;
                        }
                        if ("under_review".equals(status)) {
                            // Mantener para actualización de precio; excluir más abajo en pasos de promo.
                            mlasUnderReview.add(mla);
                            log.info("ML - Item {} en under_review: se actualiza precio, se saltean promos", mla);
                            continue;
                        }
                        log.warn("ML - Item {} omitido (estado: '{}')", mla, status);
                        mlasAOmitir.add(mla);
                    }

                    if (!mlasAOmitir.isEmpty()) {
                        mapaMLporMla = new LinkedHashMap<>(mapaMLporMla);
                        mlasAOmitir.forEach(mapaMLporMla::remove);
                        addLogWarn("Items no activos omitidos: " + mlasAOmitir.size() + ". Quedan " + mapaMLporMla.size() + " MLAs");
                    }
                    if (!mlasUnderReview.isEmpty()) {
                        addLogWarn(mlasUnderReview.size() + " items en under_review: se actualizarán precios pero se saltean en promociones");
                    }
                }
            }

            // Paso 5c: Obtener datos actuales (precio + variaciones) y determinar qué items cambian de precio.
            // Se usa en Paso 1 (excluir promos) y Paso 5 (actualizar precios) para saltear los que ya tienen el precio correcto.
            Map<String, PrecioProductoInfo> mapaMLCambianPrecio = Map.of();
            Map<String, List<Long>> variacionesPorMla = new LinkedHashMap<>();
            if (necesitaML && !mapaMLporMla.isEmpty() && (request.excluirPromociones() || request.preciosMl())) {
                actualizarEstado("Obteniendo datos actuales (precio + variaciones) de " + mapaMLporMla.size() + " items...",
                        0, mapaMLporMla.size());
                List<String> mlasParaDatos = new ArrayList<>(mapaMLporMla.keySet());
                Map<String, JsonNode> datosPorMla = mlService.obtenerDatosItemsPorLote(mlasParaDatos);

                Map<String, PrecioProductoInfo> cambian = new LinkedHashMap<>();
                int sinCambios = 0;
                int sinDatos = 0;
                for (var entry : mapaMLporMla.entrySet()) {
                    String mla = entry.getKey();
                    PrecioProductoInfo info = entry.getValue();
                    JsonNode body = datosPorMla.get(mla);
                    if (body == null) {
                        log.warn("ML - No se obtuvieron datos para {}, se omite para evitar borrar variaciones", mla);
                        sinDatos++;
                        continue;
                    }

                    JsonNode variaciones = body.path("variations");
                    if (variaciones.isArray() && !variaciones.isEmpty()) {
                        List<Long> ids = new ArrayList<>();
                        for (JsonNode v : variaciones) {
                            long varId = v.path("id").asLong(0);
                            if (varId > 0) ids.add(varId);
                        }
                        variacionesPorMla.put(mla, ids);
                    }

                    if (precioYaCoincide(body, info.pvp())) {
                        sinCambios++;
                        continue;
                    }
                    cambian.put(mla, info);
                }
                mapaMLCambianPrecio = cambian;

                if (sinCambios > 0) {
                    addLog(sinCambios + " items ya están al precio correcto, no se actualizarán ni excluirán");
                }
                if (sinDatos > 0) {
                    addLogWarn(sinDatos + " items omitidos por falta de datos en multiget");
                }
            }

            if (cancelado()) return;

            // PASO 0a: Importar costos desde DUX (incremental por fecha)
            if (request.importarCostosDux()) {
                actualizarEstado("Importando costos desde DUX...", 0, 0);
                try {
                    // Leer última fecha de importación
                    String ultimaFechaStr = getConfigStr("ultima_fecha_dux");
                    LocalDateTime desde = null;
                    if (ultimaFechaStr != null && !ultimaFechaStr.isBlank()) {
                        try {
                            desde = LocalDateTime.parse(ultimaFechaStr);
                        } catch (Exception e) {
                            addLogWarn("Fecha de última importación DUX inválida: " + ultimaFechaStr);
                        }
                    }

                    ImportDuxResultDTO duxResult = duxService.importarProductosSincrono(tracker.getFlagCancelado(), this::addLog, desde);
                    duxImportActualizados = duxResult.productosActualizados();
                    duxImportTotal = duxResult.totalProductosDux();
                    duxImportErrores = duxResult.errores().size();
                    addLog("DUX: " + duxImportActualizados + " actualizados de " + duxImportTotal
                            + " (" + duxImportErrores + " errores, "
                            + duxResult.productosNoEncontrados() + " no encontrados)");

                    // Guardar fecha de esta importación para la próxima vez
                    guardarConfig("ultima_fecha_dux", LocalDateTime.now().toString());
                } catch (Exception e) {
                    addLogWarn("Error importando desde DUX: " + e.getMessage());
                }
            }

            if (cancelado()) return;

            // PASO 0a-bis: Bajar títulos KT Gastro desde Tienda Nube y persistir en `tituloWeb`.
            // Se ejecuta antes del cálculo de precios para que los exports salgan con títulos actualizados.
            if (request.bajarTitulosNube()) {
                actualizarEstado("Descargando títulos KT Gastro desde Tienda Nube...", 0, 0);
                try {
                    Map<String, String> titulos = nubeService.obtenerTitulosPorSku(TiendaNubeService.STORE_GASTRO);
                    if (titulos.isEmpty()) {
                        addLogWarn("Tienda Nube no devolvió títulos (verificar credenciales/conectividad)");
                    } else {
                        addLog("Tienda Nube: " + titulos.size() + " SKUs con título descargados");
                        int[] r = self.actualizarTitulosWebEnDb(titulos);
                        titulosActualizados = r[0];
                        titulosSinCambio = r[1];
                        titulosSinSku = r[2];
                        addLog("Títulos: " + titulosActualizados + " actualizados, "
                                + titulosSinCambio + " sin cambios, "
                                + titulosSinSku + " SKUs sin match en DB");
                    }
                } catch (Exception e) {
                    addLogWarn("Error al sincronizar títulos desde Tienda Nube: " + e.getMessage());
                }
            }

            if (cancelado()) return;

            // PASO 0b: Generar precios de envío para MLAs que no tienen
            if (request.generarEnvio()) {
                List<Mla> mlasSinEnvio = mlaRepository.findByMlaIsNotNullAndPrecioEnvioIsNull();
                if (mlasSinEnvio.isEmpty()) {
                    addLog("Envío: todos los MLAs ya tienen precio de envío");
                } else {
                    addLog("Calculando precio de envío para " + mlasSinEnvio.size() + " MLAs sin costo...");
                    int total = mlasSinEnvio.size();
                    for (int i = 0; i < total; i++) {
                        if (cancelado()) return;
                        Mla mla = mlasSinEnvio.get(i);
                        try {
                            CostoEnvioResponseDTO r = mlService.calcularCostoEnvioGratis(mla.getMla());
                            if (r.costoEnvioSinIva() != null && r.costoEnvioSinIva().compareTo(BigDecimal.ZERO) > 0) {
                                envioOk++;
                            } else {
                                envioErr++;
                            }
                        } catch (Exception e) {
                            envioErr++;
                            log.warn("Error calculando envío para MLA {}: {}", mla.getMla(), e.getMessage());
                        }
                        if ((i + 1) % 20 == 0 || i + 1 == total) {
                            actualizarEstado("Calculando envío: " + (i + 1) + "/" + total, i + 1, total);
                        }
                    }
                    addLog("Envío: " + envioOk + " ok, " + envioErr + " errores");
                }
            }

            if (cancelado()) return;

            // PASO 1: Excluir de promociones (solo items que cambian de precio, sin under_review)
            if (request.excluirPromociones() && !mapaMLCambianPrecio.isEmpty()) {
                Map<String, PrecioProductoInfo> excluibles = filtrarSinUnderReview(mapaMLCambianPrecio, mlasUnderReview);
                if (!excluibles.isEmpty()) {
                    addLog("Excluyendo de promociones (" + excluibles.size() + " items)...");
                    int[] r = excluirDePromociones(excluibles);
                    excluidosOk = r[0];
                    excluidosErr = r[1];
                    addLog("Excluidos: " + excluidosOk + " ok, " + excluidosErr + " errores");
                }
            }

            if (cancelado()) return;

            // PASO 2: DUX ML
            if (request.duxMl() && !preciosML.isEmpty() && config.listaPreciosMl() != null) {
                addLog("Subiendo precios DUX ML...");
                Map<String, DuxService.ProductoPrecioData> duxMap = construirMapaDux(preciosML, Boolean.TRUE.equals(config.sinIvaMl()));
                duxMlProductos = duxMap.size();
                duxMlEstado = subirPreciosDux(duxMap, config.listaPreciosMl());
                addLog("DUX ML: " + duxMlProductos + " productos (" + duxMlEstado + ")");
            }

            if (cancelado()) return;

            // PASO 3: DUX Gastro
            if (request.duxGastro() && config.canalGastro() != null && config.listaPreciosGastro() != null) {
                addLog("Subiendo precios DUX Gastro...");
                List<ProductoCanalPrecio> preciosGastro = cargarPrecios(config.canalGastro(), config.cuotasGastro());
                Map<String, DuxService.ProductoPrecioData> duxMapGastro = construirMapaDux(preciosGastro, Boolean.TRUE.equals(config.sinIvaGastro()));
                duxGastroProductos = duxMapGastro.size();
                duxGastroEstado = subirPreciosDux(duxMapGastro, config.listaPreciosGastro());
                addLog("DUX Gastro: " + duxGastroProductos + " productos (" + duxGastroEstado + ")");
            }

            if (cancelado()) return;

            // PASO 4: DUX Nube
            if (request.duxNube() && config.canalHogar() != null && config.listaPreciosHogar() != null) {
                addLog("Subiendo precios DUX Nube...");
                List<ProductoCanalPrecio> preciosNube = cargarPrecios(config.canalHogar(), config.cuotasHogar());
                Map<String, DuxService.ProductoPrecioData> duxMapNube = construirMapaDux(preciosNube, Boolean.TRUE.equals(config.sinIvaHogar()));
                duxNubeProductos = duxMapNube.size();
                duxNubeEstado = subirPreciosDux(duxMapNube, config.listaPreciosHogar());
                addLog("DUX Nube: " + duxNubeProductos + " productos (" + duxNubeEstado + ")");
            }

            if (cancelado()) return;

            // PASO 5: Actualizar precios Nube
            if (request.preciosNube() && config.canalHogar() != null) {
                addLog("Actualizando precios TiendaNube...");
                List<ProductoCanalPrecio> preciosNubeDirecto = cargarPrecios(config.canalHogar(), config.cuotasHogar());
                int[] r = actualizarPreciosNube(preciosNubeDirecto);
                nubeOk = r[0];
                nubeErr = r[1];
                addLog("TiendaNube: " + nubeOk + " ok, " + nubeErr + " errores");
            }

            if (cancelado()) return;

            // PASO 6: Actualizar precios ML (solo items que cambian de precio)
            if (request.preciosMl() && !mapaMLCambianPrecio.isEmpty()) {
                addLog("Actualizando precios ML (" + mapaMLCambianPrecio.size() + " items)...");
                int[] r = actualizarPreciosML(mapaMLCambianPrecio, variacionesPorMla);
                mlOk = r[0];
                mlErr = r[1];
                addLog("Precios ML: " + mlOk + " ok, " + mlErr + " errores");
            }

            if (cancelado()) return;

            // PASO 7: Incluir en promociones (sin under_review).
            // Si 'Excluir promociones' también está activo, reconciliamos en la misma pasada: los items con
            // promos started cuyo descuento quedó desalineado se sacan (DELETE) y se re-incluyen con el % vigente.
            if (request.incluirPromociones() && !mapaMLporMla.isEmpty()) {
                Map<String, PrecioProductoInfo> incluibles = filtrarSinUnderReview(mapaMLporMla, mlasUnderReview);
                if (!incluibles.isEmpty()) {
                    addLog("Incluyendo en promociones (" + incluibles.size() + " items)...");
                    int sellerPct = config.sellerCampaignPct() != null ? config.sellerCampaignPct() : 0;
                    int dealPct = config.dealPct() != null ? config.dealPct() : 0;
                    int smartPct = config.smartPct() != null ? config.smartPct() : 0;
                    boolean reconciliarStale = request.excluirPromociones();
                    ResultadoInclusion r = incluirEnPromociones(incluibles, reconciliarStale, sellerPct, dealPct, smartPct);
                    promoOk = r.incluidos();
                    // "errores" reales = perdidos por error de API + excepciones. sinCandidate/noElegible/yaConfigurado no son fallas.
                    promoErr = r.apiError() + r.errores();
                    StringBuilder sb = new StringBuilder("Promociones: ")
                            .append(r.incluidos()).append(" incluidos");
                    if (reconciliarStale) {
                        sb.append(" (").append(r.excluidosStale()).append(" reconciliados por descuento desalineado)")
                          .append(", ").append(r.yaConfigurado()).append(" ya con el % vigente");
                    }
                    sb.append(", ").append(r.sinCandidate()).append(" sin candidate")
                      .append(", ").append(r.noElegible()).append(" no elegibles")
                      .append(", ").append(r.apiError()).append(" error de API")
                      .append(", ").append(r.errores()).append(" errores");
                    addLog(sb.toString());
                }
            }

            // Resumen final (un bloque en el log vivo + completar tracker).
            int totalOk = envioOk + excluidosOk + mlOk + promoOk + nubeOk;
            int totalErr = envioErr + excluidosErr + mlErr + promoErr + nubeErr;
            addLog("─────── Resumen ───────");
            if (duxImportTotal > 0) {
                addLog("DUX import: " + duxImportActualizados + " actualizados / " + duxImportTotal + " totales (" + duxImportErrores + " errores)");
            }
            if (titulosActualizados + titulosSinCambio + titulosSinSku > 0) {
                addLog("Títulos KT Gastro: " + titulosActualizados + " actualizados, " + titulosSinCambio + " sin cambio, " + titulosSinSku + " sin match");
            }
            if (envioOk + envioErr > 0) {
                addLog("Envíos: " + envioOk + " ok, " + envioErr + " errores");
            }
            if (excluidosOk + excluidosErr > 0) {
                addLog("Excluidos de promos: " + excluidosOk + " ok, " + excluidosErr + " errores");
            }
            if (duxMlProductos > 0) {
                addLog("DUX ML: " + duxMlProductos + " productos (" + duxMlEstado + ")");
            }
            if (duxGastroProductos > 0) {
                addLog("DUX Gastro: " + duxGastroProductos + " productos (" + duxGastroEstado + ")");
            }
            if (duxNubeProductos > 0) {
                addLog("DUX Nube: " + duxNubeProductos + " productos (" + duxNubeEstado + ")");
            }
            if (nubeOk + nubeErr > 0) {
                addLog("TiendaNube: " + nubeOk + " ok, " + nubeErr + " errores");
            }
            if (mlOk + mlErr > 0) {
                addLog("Precios ML: " + mlOk + " ok, " + mlErr + " errores");
            }
            if (promoOk + promoErr > 0) {
                addLog("Promociones: " + promoOk + " ok, " + promoErr + " errores");
            }
            addLogOk("Sincronización completada — Total: " + totalOk + " ok, " + totalErr + " errores");
            tracker.completar(0, totalOk, totalErr, "Proceso finalizado");

        } catch (Exception e) {
            log.error("Error en sincronización de precios", e);
            addLogError("ERROR: " + e.getMessage());
            tracker.completarConError(e.getMessage());
        } finally {
            resultado = new SincronizacionResultDTO(
                    duxImportActualizados, duxImportTotal, duxImportErrores,
                    titulosActualizados, titulosSinCambio, titulosSinSku,
                    envioOk, envioErr,
                    excluidosOk, excluidosErr,
                    duxMlProductos, duxMlEstado,
                    duxGastroProductos, duxGastroEstado,
                    duxNubeProductos, duxNubeEstado,
                    mlOk, mlErr, promoOk, promoErr, nubeOk, nubeErr,
                    List.copyOf(logEntries)
            );
            // Safety: idempotente. completar/completarConError ya liberaron, este es no-op.
            tracker.liberar();
        }
    }

    // ==================== PASOS DE SINCRONIZACIÓN ====================

    private int[] excluirDePromociones(Map<String, PrecioProductoInfo> mapaMLporMla) {
        int total = mapaMLporMla.size();
        actualizarEstado("Excluyendo de promociones: 0/" + total, 0, total);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (String mla : mapaMLporMla.keySet()) {
            tasks.add(() -> mlService.removeAllItemPromotions(mla));
        }

        int[] result = ejecutarBloqueParalelo(tasks, "Excluyendo de promociones");
        return new int[]{result[0], result[1] + result[2]};
    }

    private String subirPreciosDux(Map<String, DuxService.ProductoPrecioData> duxMap, String listaPrecios) {
        try {
            long idLista = duxService.obtenerIdListaPrecio(listaPrecios);
            if (duxMap.isEmpty() || idLista == 0) return "sin productos";

            int idProceso = duxService.modificarListaPrecios(duxMap, idLista);
            if (idProceso != 0) {
                String estadoProceso = duxService.obtenerEstadoProceso(idProceso);
                return estadoProceso != null ? estadoProceso : "proceso " + idProceso;
            }
            return "sin ID de proceso";
        } catch (Exception e) {
            log.error("Error subiendo precios a DUX ({}): {}", listaPrecios, e.getMessage());
            return "error: " + e.getMessage();
        }
    }

    private int[] actualizarPreciosML(Map<String, PrecioProductoInfo> mapaMLCambianPrecio,
                                       Map<String, List<Long>> variacionesPorMla) {
        int total = mapaMLCambianPrecio.size();
        actualizarEstado("Actualizando precios ML: 0/" + total, 0, total);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (var entry : mapaMLCambianPrecio.entrySet()) {
            String mla = entry.getKey();
            double precio = entry.getValue().pvp();
            List<Long> variationIds = variacionesPorMla.get(mla);

            tasks.add(() -> {
                if (variationIds != null && !variationIds.isEmpty()) {
                    // PUT único con array de variaciones (todas al mismo precio)
                    return mlService.updateItemPriceConVariaciones(mla, variationIds, precio);
                }
                return mlService.updateItemPrice(mla, precio);
            });
        }

        int[] result = ejecutarBloqueParalelo(tasks, "Actualizando precios ML");
        return new int[]{result[0], result[1] + result[2]};
    }

    /**
     * Devuelve true si el precio actual (del body de ML) ya coincide con el precio nuevo a aplicar.
     * Para items con variaciones, todas deben coincidir.
     */
    /**
     * Actualiza el campo {@code tituloWeb} de los productos cuyos SKUs aparecen en el mapa.
     * Solo se persisten cambios reales (skip si el título coincide). Trim + uppercase.
     *
     * @return {@code [actualizados, sinCambio, sinSkuEnDb]}
     */
    @Transactional
    public int[] actualizarTitulosWebEnDb(Map<String, String> titulosPorSku) {
        if (titulosPorSku == null || titulosPorSku.isEmpty()) return new int[]{0, 0, 0};

        List<String> skus = new ArrayList<>(titulosPorSku.keySet());
        List<Producto> productos = productoRepository.findBySkuIn(skus);

        Set<String> skusEncontrados = new HashSet<>(productos.size());
        int actualizados = 0;
        int sinCambio = 0;
        for (Producto p : productos) {
            String sku = p.getSku();
            skusEncontrados.add(sku);
            String nuevoTitulo = titulosPorSku.get(sku);
            if (nuevoTitulo == null) continue;
            nuevoTitulo = nuevoTitulo.trim();
            if (nuevoTitulo.isEmpty()) continue;
            // El campo tiene length=100 — truncar para no fallar el insert.
            if (nuevoTitulo.length() > 100) {
                nuevoTitulo = nuevoTitulo.substring(0, 100);
            }
            if (nuevoTitulo.equals(p.getTituloWeb())) {
                sinCambio++;
                continue;
            }
            p.setTituloWeb(nuevoTitulo);
            actualizados++;
        }

        int sinSku = skus.size() - skusEncontrados.size();
        return new int[]{actualizados, sinCambio, sinSku};
    }

    /**
     * Devuelve una vista del mapa sin los MLAs en under_review (ML rechaza promos en ese estado).
     */
    private Map<String, PrecioProductoInfo> filtrarSinUnderReview(
            Map<String, PrecioProductoInfo> mapa, Set<String> underReview) {
        if (underReview == null || underReview.isEmpty()) return mapa;
        Map<String, PrecioProductoInfo> copia = new LinkedHashMap<>(mapa);
        copia.keySet().removeAll(underReview);
        return copia;
    }

    private boolean precioYaCoincide(JsonNode body, double precioNuevo) {
        JsonNode variaciones = body.path("variations");
        if (variaciones.isArray() && !variaciones.isEmpty()) {
            for (JsonNode v : variaciones) {
                if (Math.abs(v.path("price").asDouble(-1) - precioNuevo) >= 0.01) {
                    return false;
                }
            }
            return true;
        }
        return Math.abs(body.path("price").asDouble(-1) - precioNuevo) < 0.01;
    }

    /** Tipos de promo que el flujo procesa. Si el seller no tiene ninguna activa de estos tipos,
     *  podemos saltearnos las consultas por item (gran ahorro en runs grandes). */
    private static final Set<String> TIPOS_PROMO_SOPORTADOS =
            Set.of("DEAL", "SELLER_CAMPAIGN", "SMART", "PRICE_MATCHING", "MARKETPLACE_CAMPAIGN");

    /**
     * Resultado detallado de la inclusión en promociones.
     * <ul>
     *   <li>{@code incluidos}: items con POST 2xx en al menos una promo (incluye los reconciliados).</li>
     *   <li>{@code yaConfigurado}: items que ya estaban en promo(s) con la config correcta, sin candidates
     *       nuevos que procesar (solo aplica en modo reconcile).</li>
     *   <li>{@code sinCandidate}: items sin promo candidate ni started accionable.</li>
     *   <li>{@code noElegible}: items con candidate(s) pero ninguno pasó los filtros (rango, tope, credibilidad).</li>
     *   <li>{@code apiError}: items que no quedaron incluidos porque al menos una candidate falló por error de API.</li>
     *   <li>{@code errores}: excepciones inesperadas en la tarea.</li>
     *   <li>{@code excluidosStale}: items con promos started desalineadas (DELETE + re-include); subconjunto
     *       de incluidos+apiError.</li>
     * </ul>
     */
    private record ResultadoInclusion(int incluidos, int yaConfigurado, int sinCandidate, int noElegible,
                                      int apiError, int errores, int excluidosStale) {}

    /**
     * Flujo por-item: por cada MLA consulta sus promociones y procesa cada candidate.
     * <p>
     * Si {@code reconciliarStale=true}: además evalúa las promos en status=started. Si su descuento actual
     * (seller_percentage) no coincide con la config vigente (ver {@link #esStaleStarted}), saca el item de
     * TODAS sus promos (DELETE) y lo re-incluye con los % vigentes. Esto corrige items que NO cambiaron de
     * precio base pero quedaron con un descuento desactualizado.
     */
    private ResultadoInclusion incluirEnPromociones(Map<String, PrecioProductoInfo> mapaMLporMla,
                                                    boolean reconciliarStale,
                                                    int sellerCampaignPct, int dealPct, int smartPct) {
        int total = mapaMLporMla.size();

        // Short-circuit: 1 sola request al endpoint del seller. Si no hay ninguna promo activa
        // de los tipos soportados, evitamos N consultas /seller-promotions/items/{mla}.
        try {
            String userId = mlService.getUserId();
            String sellerPromosJson = mlService.obtenerPromocionesDelSeller(userId);
            if (sellerPromosJson != null) {
                JsonNode root = objectMapper.readTree(sellerPromosJson);
                JsonNode results = root.path("results");
                int activasSoportadas = 0;
                if (results.isArray()) {
                    for (JsonNode p : results) {
                        String type = p.path("type").asString("");
                        String status = p.path("status").asString("");
                        if (TIPOS_PROMO_SOPORTADOS.contains(type)
                                && ("started".equals(status) || "pending".equals(status))) {
                            activasSoportadas++;
                        }
                    }
                }
                if (activasSoportadas == 0) {
                    addLog("Sin promociones activas soportadas en ML: se saltean " + total + " consultas por item");
                    return new ResultadoInclusion(0, 0, total, 0, 0, 0, 0);
                }
                addLog(activasSoportadas + " promo(s) activa(s) soportadas en ML; consultando candidates por item...");
            }
        } catch (Exception e) {
            // No bloqueante: si falla la consulta global, seguimos con el flujo por-item (sin optimización).
            log.warn("ML - No se pudo verificar promociones del seller (sigue flujo por item): {}", e.getMessage());
        }

        actualizarEstado("Incluyendo en promociones: 0/" + total, 0, total);

        AtomicInteger yaConfigurado = new AtomicInteger();
        AtomicInteger sinCandidate = new AtomicInteger();
        AtomicInteger noElegible = new AtomicInteger();
        AtomicInteger apiError = new AtomicInteger();
        AtomicInteger excluidosStale = new AtomicInteger();

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (var entry : mapaMLporMla.entrySet()) {
            String mla = entry.getKey();
            int topePromocion = entry.getValue().topePromocion();

            tasks.add(() -> {
                String promosJson = mlService.obtenerPromocionesDelItem(mla);
                if (promosJson == null) {
                    sinCandidate.incrementAndGet();
                    return false;
                }
                JsonNode promotions = objectMapper.readTree(promosJson);
                if (!promotions.isArray() || promotions.isEmpty()) {
                    sinCandidate.incrementAndGet();
                    return false;
                }

                // Primera pasada (solo en modo reconcile): clasificar las started.
                //  - needsDelete: hay alguna started con descuento desalineado.
                //  - teniaStartedOK: hay alguna started de tipo manejado y bien configurada (para distinguir
                //    "ya configurado" de "sin promo").
                boolean needsDelete = false;
                boolean teniaStartedOK = false;
                if (reconciliarStale) {
                    for (JsonNode promo : promotions) {
                        if (!"started".equals(promo.path("status").asString(""))) continue;
                        if (esStaleStarted(promo, topePromocion, sellerCampaignPct, dealPct, smartPct)) {
                            needsDelete = true;
                        } else if (TIPOS_PROMO_SOPORTADOS.contains(promo.path("type").asString(""))) {
                            teniaStartedOK = true;
                        }
                    }
                }

                // Promos a procesar como candidates:
                //  - Si needsDelete: tras el DELETE, forzamos status=candidate sobre las started+candidate.
                //  - Si no: las originales (solo se aceptarán las que ya estén en candidate).
                List<JsonNode> promosAProcesar = new ArrayList<>();
                if (needsDelete) {
                    if (!mlService.removeAllItemPromotions(mla)) {
                        apiError.incrementAndGet();
                        return false;
                    }
                    excluidosStale.incrementAndGet();
                    for (JsonNode promo : promotions) {
                        String st = promo.path("status").asString("");
                        if (!"started".equals(st) && !"candidate".equals(st)) continue;
                        ObjectNode fake = (ObjectNode) promo.deepCopy();
                        fake.put("status", "candidate"); // forzar para que procesarPromocion lo acepte
                        promosAProcesar.add(fake);
                    }
                } else {
                    promotions.forEach(promosAProcesar::add);
                }

                boolean incluidoEnAlguna = false;
                boolean tuvoApiError = false;
                boolean teniaCandidate = false;
                for (JsonNode promo : promosAProcesar) {
                    if (!"candidate".equals(promo.path("status").asString(null))) continue;
                    teniaCandidate = true;

                    String promoId = promo.path("id").asString(null);
                    String promoType = promo.path("type").asString(null);
                    if (promoId == null || promoType == null) continue;

                    MercadoLibreService.IncludeResult ir = procesarPromocion(mla, promo, promoId, promoType,
                            topePromocion, sellerCampaignPct, dealPct, smartPct);
                    if (ir == MercadoLibreService.IncludeResult.INCLUDED) {
                        incluidoEnAlguna = true;
                    } else if (ir == MercadoLibreService.IncludeResult.API_ERROR) {
                        tuvoApiError = true;
                    }
                }

                if (incluidoEnAlguna) return true;

                // No quedó incluido en ninguna nueva promo: clasificar el motivo.
                if (tuvoApiError) {
                    apiError.incrementAndGet();
                } else if (teniaCandidate) {
                    noElegible.incrementAndGet();
                } else if (teniaStartedOK) {
                    yaConfigurado.incrementAndGet();
                } else {
                    sinCandidate.incrementAndGet();
                }
                return false;
            });
        }

        int[] result = ejecutarBloqueParalelo(tasks, "Incluyendo en promociones");
        if (reconciliarStale && excluidosStale.get() > 0) {
            addLog("Reconciliados (descuento desalineado, re-incluidos con el % vigente): " + excluidosStale.get());
        }
        return new ResultadoInclusion(result[0], yaConfigurado.get(), sinCandidate.get(), noElegible.get(),
                apiError.get(), result[2], excluidosStale.get());
    }

    /**
     * Determina si una promo en status=started quedó desalineada respecto de la config actual
     * (y por lo tanto requiere DELETE + re-inclusión).
     * <ul>
     *   <li>DEAL / SELLER_CAMPAIGN: stale si el seller_percentage actual ≠ el % que aplicaríamos hoy
     *       (tope por MLA si existe, si no el % global).</li>
     *   <li>SMART / PRICE_MATCHING / MARKETPLACE_CAMPAIGN: stale si el seller_percentage actual supera
     *       el límite efectivo.</li>
     *   <li>Otros tipos (DOD, LIGHTNING, etc.): nunca stale — no los manejamos.</li>
     * </ul>
     */
    private boolean esStaleStarted(JsonNode promo, int topePromocion,
                                   int sellerCampaignPct, int dealPct, int smartPct) {
        if (!"started".equals(promo.path("status").asString(""))) return false;
        String type = promo.path("type").asString("");
        int currentPct = promo.path("seller_percentage").asInt(0);

        return switch (type) {
            case "DEAL" -> currentPct != (topePromocion > 0 ? topePromocion : dealPct);
            case "SELLER_CAMPAIGN" -> currentPct != (topePromocion > 0 ? topePromocion : sellerCampaignPct);
            case "SMART", "PRICE_MATCHING", "MARKETPLACE_CAMPAIGN" ->
                    currentPct > (topePromocion > 0 ? topePromocion : smartPct);
            default -> false;
        };
    }

    private MercadoLibreService.IncludeResult procesarPromocion(String mla, JsonNode promo, String promoId, String promoType,
                                      int topePromocion, int sellerCampaignPct, int dealPct, int smartPct) {
        return switch (promoType) {
            case "DEAL" -> {
                double originalPrice = promo.path("original_price").asDouble(0);
                double minPrice = promo.path("min_discounted_price").asDouble(0);
                double maxPrice = promo.path("max_discounted_price").asDouble(0);
                if (originalPrice <= 0 || minPrice <= 0 || maxPrice <= 0) yield MercadoLibreService.IncludeResult.REJECTED;

                int effectivePct = topePromocion > 0 ? topePromocion : dealPct;
                double discounted = BigDecimal.valueOf(originalPrice * (100.0 - effectivePct) / 100.0)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
                if (discounted >= minPrice && discounted <= maxPrice) {
                    yield mlService.addItemToPromotion(promoId, mla, "DEAL", discounted, null);
                }
                log.info("ML - Precio fuera de rango para DEAL en {}: {} no está entre {} y {}",
                        mla, discounted, minPrice, maxPrice);
                yield MercadoLibreService.IncludeResult.REJECTED;
            }
            case "SELLER_CAMPAIGN" -> {
                double originalPrice = promo.path("original_price").asDouble(0);
                double minPrice = promo.path("min_discounted_price").asDouble(0);
                double maxPrice = promo.path("max_discounted_price").asDouble(0);
                if (originalPrice <= 0 || minPrice <= 0 || maxPrice <= 0) yield MercadoLibreService.IncludeResult.REJECTED;

                int effectivePct = topePromocion > 0 ? topePromocion : sellerCampaignPct;
                double discounted = BigDecimal.valueOf(originalPrice * (100.0 - effectivePct) / 100.0)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
                if (discounted >= minPrice && discounted <= maxPrice) {
                    yield mlService.addItemToPromotion(promoId, mla, "SELLER_CAMPAIGN", discounted, null);
                }
                log.info("ML - Precio fuera de rango para SELLER_CAMPAIGN en {}: {} no está entre {} y {}",
                        mla, discounted, minPrice, maxPrice);
                yield MercadoLibreService.IncludeResult.REJECTED;
            }
            case "SMART", "PRICE_MATCHING", "MARKETPLACE_CAMPAIGN" -> {
                String offerId = promo.path("ref_id").asString(null);
                int sellerPercentage = promo.path("seller_percentage").asInt(0);
                int effectiveLimit = topePromocion > 0 ? topePromocion : smartPct;

                // MARKETPLACE_CAMPAIGN en candidate no trae ref_id (se asigna al incluirlo).
                // Solo SMART y PRICE_MATCHING requieren offer_id al momento del POST.
                boolean requiereOfferId = "SMART".equals(promoType) || "PRICE_MATCHING".equals(promoType);
                if (requiereOfferId && offerId == null) yield MercadoLibreService.IncludeResult.REJECTED;
                if (sellerPercentage <= 0) yield MercadoLibreService.IncludeResult.REJECTED;
                if (sellerPercentage > effectiveLimit) {
                    log.info("ML - Item {} no incluido en {}: seller_percentage {}% > límite {}%",
                            mla, promoType, sellerPercentage, effectiveLimit);
                    yield MercadoLibreService.IncludeResult.REJECTED;
                }
                yield mlService.addItemToPromotion(promoId, mla, promoType, 0, offerId);
            }
            default -> MercadoLibreService.IncludeResult.REJECTED;
        };
    }

    private int[] actualizarPreciosNube(List<ProductoCanalPrecio> precios) {
        int total = precios.size();
        if (total == 0) return new int[]{0, 0};

        String storeName = "KT HOGAR";

        actualizarEstado("Descargando catálogo " + storeName + "...", 0, total);
        Map<String, TiendaNubeService.VarianteInfo> mapa = nubeService.listarVariantesPorSku(storeName);
        if (mapa.isEmpty()) {
            addLogWarn("No se pudo descargar el catálogo de " + storeName);
            return new int[]{0, total};
        }
        addLog("Catálogo " + storeName + " cargado: " + mapa.size() + " SKUs");

        Map<Long, List<TiendaNubeService.VariantePriceUpdate>> updatesPorProducto = new LinkedHashMap<>();
        Map<Long, Integer> skusPorProducto = new LinkedHashMap<>();
        Set<Long> variantesEncoladas = new HashSet<>();
        Set<String> skusProcesados = new HashSet<>();
        int noEncontrados = 0;
        int yaCoinciden = 0;

        for (ProductoCanalPrecio pcp : precios) {
            String sku = pcp.getProducto().getSku();
            if (!skusProcesados.add(sku)) continue; // dedup entrada (mismo SKU en múltiples cuotas)

            BigDecimal pvp = pcp.getPvp();
            BigDecimal pvpInflado = pcp.getPvpInflado();

            // Preserva el redondeo a entero y agrega 2 decimales para cumplir formato "19.00" de la doc oficial.
            String price = pvpInflado != null && pvpInflado.compareTo(BigDecimal.ZERO) > 0
                    ? pvpInflado.setScale(0, RoundingMode.HALF_UP).setScale(2).toPlainString() : null;
            String promotionalPrice = pvp != null && pvp.compareTo(BigDecimal.ZERO) > 0
                    ? pvp.setScale(0, RoundingMode.HALF_UP).setScale(2).toPlainString() : null;

            TiendaNubeService.VarianteInfo info = mapa.get(sku);
            if (info == null) {
                noEncontrados++;
                continue;
            }

            if (precioYaCoincideNube(info, price, promotionalPrice)) {
                yaCoinciden++;
                continue;
            }

            // Dedup defensivo por variantId (evita enviar la misma variante 2 veces en el PATCH)
            if (!variantesEncoladas.add(info.variantId())) continue;

            updatesPorProducto
                    .computeIfAbsent(info.productId(), k -> new ArrayList<>())
                    .add(new TiendaNubeService.VariantePriceUpdate(info.variantId(), price, promotionalPrice));
            skusPorProducto.merge(info.productId(), 1, Integer::sum);
        }

        if (yaCoinciden > 0) {
            addLog(yaCoinciden + " SKUs ya tienen el precio correcto en " + storeName + ", no se actualizarán");
        }
        if (noEncontrados > 0) {
            addLogWarn(noEncontrados + " SKUs no encontrados en " + storeName);
        }

        if (updatesPorProducto.isEmpty()) {
            addLog("No hay cambios de precio para subir a " + storeName);
            return new int[]{yaCoinciden, noEncontrados};
        }

        int totalProductos = updatesPorProducto.size();
        actualizarEstado("Actualizando precios Nube: 0/" + totalProductos + " productos", 0, totalProductos);

        AtomicInteger skusOkActualizados = new AtomicInteger(0);
        AtomicInteger skusErrActualizados = new AtomicInteger(0);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (var entry : updatesPorProducto.entrySet()) {
            long productId = entry.getKey();
            List<TiendaNubeService.VariantePriceUpdate> updates = entry.getValue();
            int cantSkus = skusPorProducto.getOrDefault(productId, updates.size());
            tasks.add(() -> {
                int ok = nubeService.actualizarVariantesDeProducto(storeName, productId, updates);
                skusOkActualizados.addAndGet(ok);
                skusErrActualizados.addAndGet(cantSkus - ok);
                return ok > 0;
            });
        }

        ejecutarBloqueParalelo(tasks, "Actualizando precios Nube");

        int totalOk = yaCoinciden + skusOkActualizados.get();
        int totalErr = noEncontrados + skusErrActualizados.get();
        return new int[]{totalOk, totalErr};
    }

    /**
     * True si el precio actual de Nube ya coincide con el nuevo (ambos campos).
     * Un campo nuevo en null significa "no enviar" → siempre coincide para ese campo.
     */
    private boolean precioYaCoincideNube(TiendaNubeService.VarianteInfo actual,
                                         String nuevoPrice, String nuevoPromo) {
        return precioIgual(actual.price(), nuevoPrice)
                && precioIgual(actual.promotionalPrice(), nuevoPromo);
    }

    private boolean precioIgual(String actual, String nuevo) {
        if (nuevo == null) return true;
        if (actual == null) return false;
        try {
            return new BigDecimal(actual).compareTo(new BigDecimal(nuevo)) == 0;
        } catch (NumberFormatException e) {
            return actual.equals(nuevo);
        }
    }

    // ==================== EJECUCIÓN PARALELA ====================

    /**
     * Ejecuta tareas en paralelo con un pool de threads (como Generator.ejecutarBloque).
     * @return int[] {exitosos, fallidos, errores_excepcion}
     */
    private int[] ejecutarBloqueParalelo(List<Callable<Boolean>> tasks, String label) {
        if (tasks.isEmpty()) return new int[]{0, 0, 0};

        int total = tasks.size();
        AtomicInteger procesados = new AtomicInteger(0);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);
        AtomicInteger errores = new AtomicInteger(0);

        // Enviar tareas al pool compartido de Spring
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(taskExecutor.submit(task));
        }

        // Recolectar resultados
        for (Future<Boolean> f : futures) {
            try {
                Boolean result = f.get();
                if (Boolean.TRUE.equals(result)) {
                    exitosos.incrementAndGet();
                } else {
                    fallidos.incrementAndGet();
                }
            } catch (ExecutionException e) {
                errores.incrementAndGet();
                Throwable cause = e.getCause();
                log.warn("{} - Error en tarea: {}", label,
                        cause != null ? cause.getMessage() : e.getMessage());
            } catch (Exception e) {
                errores.incrementAndGet();
                log.warn("{} - Error inesperado: {}", label, e.getMessage());
            }

            int p = procesados.incrementAndGet();
            if (p % 20 == 0 || p == total) {
                actualizarEstado(label + ": " + p + "/" + total, p, total);
            }
            // Item-by-item logging (granular): cada 50 items para no saturar el log vivo.
            if (p % 50 == 0 && p < total) {
                addLog(label + ": " + p + "/" + total + " procesados");
            }
        }

        actualizarEstado(label + ": " + total + "/" + total + " completado", total, total);

        if (errores.get() > 0) {
            log.warn("{} - Finalizado con {} errores de {} tareas", label, errores.get(), total);
        }
        return new int[]{exitosos.get(), fallidos.get(), errores.get()};
    }

    // ==================== HELPERS ====================

    private record PrecioProductoInfo(double pvp, String sku, String tipo, int topePromocion) {}

    private List<ProductoCanalPrecio> cargarPrecios(String canalNombre, Integer cuotas) {
        Canal canal = canalRepository.findByNombreIgnoreCase(canalNombre)
                .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado: " + canalNombre));

        Integer cuotasParam = (cuotas != null && cuotas > 0) ? cuotas : null;
        return precioRepo.findByCanalIdAndCuotasWithProductoAndMla(canal.getId(), cuotasParam);
    }

    private Map<String, PrecioProductoInfo> construirMapaPorMla(List<ProductoCanalPrecio> precios) {
        Map<String, PrecioProductoInfo> mapa = new LinkedHashMap<>();

        for (ProductoCanalPrecio pcp : precios) {
            if (pcp.getPvp() == null || pcp.getPvp().compareTo(BigDecimal.ZERO) <= 0) continue;

            var producto = pcp.getProducto();
            Mla mla = producto.getMla();
            if (mla == null || mla.getMla() == null || mla.getMla().isBlank()) continue;

            String tipo = Boolean.TRUE.equals(producto.getEsCombo()) ? "COMBO" : "SIMPLE";
            double pvp = pcp.getPvp().setScale(0, RoundingMode.HALF_UP).doubleValue();
            int tope = mla.getTopePromocion() != null ? mla.getTopePromocion() : 0;

            // Evitar duplicados: tomar el primero
            mapa.putIfAbsent(mla.getMla(), new PrecioProductoInfo(pvp, producto.getSku(), tipo, tope));
        }
        return mapa;
    }

    private Map<String, DuxService.ProductoPrecioData> construirMapaDux(List<ProductoCanalPrecio> precios) {
        return construirMapaDux(precios, false);
    }

    private Map<String, DuxService.ProductoPrecioData> construirMapaDux(List<ProductoCanalPrecio> precios, boolean quitarIva) {
        Map<String, DuxService.ProductoPrecioData> mapa = new LinkedHashMap<>();

        for (ProductoCanalPrecio pcp : precios) {
            if (pcp.getPvp() == null || pcp.getPvp().compareTo(BigDecimal.ZERO) <= 0) continue;

            var producto = pcp.getProducto();
            String tipo = Boolean.TRUE.equals(producto.getEsCombo()) ? "COMBO" : "SIMPLE";

            BigDecimal precio = pcp.getPvp();
            if (quitarIva && producto.getIva() != null && producto.getIva().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal divisor = BigDecimal.ONE.add(producto.getIva().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                precio = precio.divide(divisor, 0, RoundingMode.HALF_UP);
            } else {
                precio = precio.setScale(0, RoundingMode.HALF_UP);
            }

            mapa.put(producto.getSku(), new DuxService.ProductoPrecioData(tipo, precio.doubleValue()));
        }
        return mapa;
    }

    private boolean cancelado() {
        if (tracker.estaCancelado()) {
            addLogWarn("Proceso cancelado por el usuario");
            // tracker.completar() detecta estaCancelado() y graba estado="cancelado".
            tracker.completar(0, 0, 0, "Cancelado por el usuario");
            return true;
        }
        return false;
    }

    // Prefijos por nivel (Unicode). El frontend puede mostrarlos tal cual, y además
    // sirven para que el LogPanel matchee colores por patrón de inicio.
    private static final String PFX_INFO = "ℹ ";
    private static final String PFX_OK = "✔ ";
    private static final String PFX_WARN = "⚠ ";
    private static final String PFX_ERROR = "✖ ";

    private void addLog(String mensaje) {
        logEntries.add(PFX_INFO + mensaje);
        log.info("SYNC - {}", mensaje);
    }

    private void addLogOk(String mensaje) {
        logEntries.add(PFX_OK + mensaje);
        log.info("SYNC - {}", mensaje);
    }

    private void addLogWarn(String mensaje) {
        logEntries.add(PFX_WARN + mensaje);
        log.warn("SYNC - {}", mensaje);
    }

    private void addLogError(String mensaje) {
        logEntries.add(PFX_ERROR + mensaje);
        log.error("SYNC - {}", mensaje);
    }

    private void actualizarEstado(String mensaje, int procesados, int total) {
        tracker.actualizar(total, procesados, 0, 0, mensaje);
    }

    private String getConfigStr(String clave) {
        return configRepo.findByClaveIgnoreCase(clave)
                .map(ConfigAutomatizacion::getValor)
                .orElse(null);
    }

    private void guardarConfig(String clave, String valor) {
        ConfigAutomatizacion entity = configRepo.findByClaveIgnoreCase(clave).orElse(null);
        if (entity != null) {
            entity.setValor(valor);
        } else {
            entity = new ConfigAutomatizacion();
            entity.setClave(clave);
            entity.setValor(valor);
        }
        configRepo.save(entity);
    }

    private Boolean getConfigBool(String clave) {
        String valor = getConfigStr(clave);
        if (valor == null) return false;
        return "true".equalsIgnoreCase(valor.trim()) || "1".equals(valor.trim()) || "si".equalsIgnoreCase(valor.trim());
    }

    private Integer getConfigInt(String clave) {
        String valor = getConfigStr(clave);
        if (valor == null) return null;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
