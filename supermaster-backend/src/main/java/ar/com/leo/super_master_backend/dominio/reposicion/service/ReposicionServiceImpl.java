package ar.com.leo.super_master_backend.dominio.reposicion.service;

import ar.com.leo.super_master_backend.apis.dux.model.FacturaDux;
import ar.com.leo.super_master_backend.apis.dux.model.PedidoDux;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService.DuxItemData;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import ar.com.leo.super_master_backend.dominio.auditoria.service.AuditoriaService;
import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.service.EstadoProcesoMasivo;
import ar.com.leo.super_master_backend.dominio.common.service.ProcesoGlobalService;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraCreateDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraLineaCreateDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.EstadoOrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompra;
import ar.com.leo.super_master_backend.dominio.orden_compra.entity.OrdenCompraLinea;
import ar.com.leo.super_master_backend.dominio.orden_compra.repository.OrdenCompraRepository;
import ar.com.leo.super_master_backend.dominio.orden_compra.service.OrdenCompraService;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.AjustePedidoDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionConfigDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionResultDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.SugerenciaReposicionDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.ReposicionConfig;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.VentaDiariaCache;
import ar.com.leo.super_master_backend.dominio.reposicion.repository.ReposicionConfigRepository;
import ar.com.leo.super_master_backend.dominio.reposicion.repository.VentaDiariaCacheRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReposicionServiceImpl implements ReposicionService {

    private final ReposicionConfigRepository configRepository;
    private final ProductoRepository productoRepository;
    private final DuxService duxService;
    private final OrdenCompraService ordenCompraService;
    private final OrdenCompraRepository ordenCompraRepository;
    private final RecalculoPrecioFacade recalculoPrecioFacade;
    private final VentaDiariaCacheRepository ventaDiariaCacheRepository;
    private final AuditoriaService auditoriaService;
    private final ProcesoGlobalService procesoGlobal;

    // Auto-inyección para proxy de Spring (@Async)
    @Lazy
    @Autowired
    private ReposicionService self;

    // Tracker reusable (estado + locks + supplier de progreso al SSE).
    private EstadoProcesoMasivo tracker;
    private volatile ReposicionResultDTO resultadoCalculo = null;

    @PostConstruct
    void initTracker() {
        this.tracker = new EstadoProcesoMasivo("reposicion", "Cálculo de reposición", procesoGlobal);
    }

    // Formatos de fecha que puede devolver DUX
    private static final DateTimeFormatter[] FORMATOS_FECHA_DUX = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a", Locale.US)
    };

    public ReposicionServiceImpl(ReposicionConfigRepository configRepository,
                                 ProductoRepository productoRepository,
                                 DuxService duxService,
                                 OrdenCompraService ordenCompraService,
                                 OrdenCompraRepository ordenCompraRepository,
                                 RecalculoPrecioFacade recalculoPrecioFacade,
                                 VentaDiariaCacheRepository ventaDiariaCacheRepository,
                                 AuditoriaService auditoriaService,
                                 ProcesoGlobalService procesoGlobal) {
        this.configRepository = configRepository;
        this.productoRepository = productoRepository;
        this.duxService = duxService;
        this.ordenCompraService = ordenCompraService;
        this.ordenCompraRepository = ordenCompraRepository;
        this.recalculoPrecioFacade = recalculoPrecioFacade;
        this.ventaDiariaCacheRepository = ventaDiariaCacheRepository;
        this.auditoriaService = auditoriaService;
        this.procesoGlobal = procesoGlobal;
    }

    // =====================================================
    // CONFIG
    // =====================================================

    @Override
    @Transactional(readOnly = true)
    public ReposicionConfigDTO obtenerConfig() {
        ReposicionConfig config = configRepository.findById(1)
                .orElseThrow(() -> new NotFoundException("Configuración de reposición no encontrada. Ejecute el script reposicion.sql"));
        return toDTO(config);
    }

    @Override
    @Transactional
    public ReposicionConfigDTO actualizarConfig(ReposicionConfigDTO dto) {
        ReposicionConfig config = configRepository.findById(1)
                .orElseThrow(() -> new NotFoundException("Configuración de reposición no encontrada"));
        Map<String, String> estadoAnterior = capturarSnapshotConfig(config);

        if (dto.mesesCobertura() != null) config.setMesesCobertura(dto.mesesCobertura());
        if (dto.diasPorPeriodo() != null) config.setDiasPorPeriodo(dto.diasPorPeriodo());
        if (dto.pesoMes1() != null) config.setPesoMes1(dto.pesoMes1());
        if (dto.pesoMes2() != null) config.setPesoMes2(dto.pesoMes2());
        if (dto.pesoMes3() != null) config.setPesoMes3(dto.pesoMes3());
        if (dto.idEmpresaDux() != null) config.setIdEmpresaDux(dto.idEmpresaDux());
        if (dto.idsSucursalDux() != null) config.setIdsSucursalDux(dto.idsSucursalDux());

        // Validar que los pesos sumen ~1.0 (tolerancia ±0.05). Si todavía no se
        // cargaron pesos (suma 0), se permite y no se valida.
        BigDecimal sumaPesos = config.getPesoMes1().add(config.getPesoMes2()).add(config.getPesoMes3());
        if (sumaPesos.compareTo(BigDecimal.ZERO) > 0
                && (sumaPesos.compareTo(new BigDecimal("0.95")) < 0
                || sumaPesos.compareTo(new BigDecimal("1.05")) > 0)) {
            throw new BadRequestException(
                    String.format("La suma de los pesos (%.2f) debe ser ~1.0 (entre 0.95 y 1.05)", sumaPesos));
        }

        configRepository.save(config);
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REPOSICION,
                1,
                "CONFIG",
                AuditoriaAccion.UPDATE,
                estadoAnterior,
                capturarSnapshotConfig(config)
        );
        return toDTO(config);
    }

    private ReposicionConfigDTO toDTO(ReposicionConfig config) {
        return new ReposicionConfigDTO(
                config.getMesesCobertura(),
                config.getDiasPorPeriodo(),
                config.getPesoMes1(),
                config.getPesoMes2(),
                config.getPesoMes3(),
                config.getIdEmpresaDux(),
                config.getIdsSucursalDux()
        );
    }

    // =====================================================
    // CÁLCULO ASYNC
    // =====================================================

    @Override
    public boolean iniciarCalculo() {
        if (!tracker.adquirir()) {
            log.warn("REPOSICIÓN - No se pudo iniciar: ya en ejecución u otro proceso activo");
            return false;
        }
        resultadoCalculo = null;
        registrarEventoProceso("calculo", "Cálculo de reposición iniciado");
        self.calcularAsync();
        return true;
    }

    @Override
    public boolean cancelarCalculo() {
        if (tracker.estaEjecutando()) {
            tracker.cancelar();
            log.info("REPOSICIÓN - Solicitud de cancelación recibida");
            registrarEventoProceso("calculo", "Cancelación de cálculo solicitada");
            return true;
        }
        return false;
    }

    @Override
    public ProcesoMasivoEstadoDTO obtenerEstadoCalculo() {
        return tracker.obtener();
    }

    @Override
    public ReposicionResultDTO obtenerResultado() {
        return resultadoCalculo;
    }

    @Override
    @Async
    public void calcularAsync() {
        LocalDateTime iniciadoEn = tracker.obtener().iniciadoEn();

        duxService.getRetryHandler().setRetryListener(msg ->
                actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn, msg));

        try {
            log.info("REPOSICIÓN - Iniciando cálculo de sugerencias...");

            // Cargar configuración
            ReposicionConfig config = configRepository.findById(1)
                    .orElseThrow(() -> new BadRequestException("Configuración de reposición no encontrada"));

            if (config.getIdEmpresaDux() == null || config.getIdsSucursalDux() == null || config.getIdsSucursalDux().isEmpty()) {
                throw new BadRequestException("Configure idEmpresaDux e idsSucursalDux antes de calcular");
            }

            int idEmpresa = config.getIdEmpresaDux();
            List<Integer> sucursales = config.getIdsSucursalDux();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            List<String> advertencias = new ArrayList<>();

            // ---- Fase 1: Stock y costo de DUX (incremental si hay fecha guardada) ----
            LocalDateTime ultimoFetch = config.getUltimoStockFetch();
            BiConsumer<Integer, Integer> stockProgress = (page, totalPages) -> {
                String msg = totalPages > 0
                        ? String.format("Obteniendo datos de DUX... página %d/%d", page, totalPages)
                        : String.format("Obteniendo datos de DUX... página %d", page);
                actualizarEstado(true, totalPages, page, 0, 0, "ejecutando", iniciadoEn, msg);
            };

            Map<String, DuxItemData> datosDux;
            if (ultimoFetch != null) {
                log.info("REPOSICIÓN - Fase 1: Actualizando datos incremental (desde {})...", ultimoFetch);
                actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                        "Actualizando datos incremental desde " + ultimoFetch.toLocalDate() + "...");
                datosDux = duxService.obtenerItemDataMap(tracker.getFlagCancelado(), ultimoFetch, stockProgress);
                log.info("REPOSICIÓN - Fase 1 completada: {} SKUs actualizados", datosDux.size());
            } else {
                log.info("REPOSICIÓN - Fase 1: Obteniendo datos completos de DUX...");
                actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn, "Obteniendo datos de DUX...");
                datosDux = duxService.obtenerItemDataMap(tracker.getFlagCancelado(), null, stockProgress);
                log.info("REPOSICIÓN - Fase 1 completada: {} SKUs con datos", datosDux.size());
            }
            if (tracker.estaCancelado()) {
                finalizarCancelado(iniciadoEn);
                return;
            }

            // Persistir stock y costo en Producto, actualizar timestamp
            actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                    String.format("Guardando datos de %d SKUs en base de datos...", datosDux.size()));
            List<Integer> costosCambiados = self.persistirDatosDux(datosDux);
            configRepository.findById(1).ifPresent(c -> {
                c.setUltimoStockFetch(LocalDateTime.now());
                configRepository.save(c);
            });

            // Recalcular precios para productos con costo modificado
            if (!costosCambiados.isEmpty()) {
                log.info("REPOSICIÓN - Recalculando precios para {} productos con costo modificado...", costosCambiados.size());
                actualizarEstado(true, costosCambiados.size(), 0, 0, 0, "ejecutando", iniciadoEn,
                        String.format("Recalculando precios (%d productos)...", costosCambiados.size()));
                for (Integer productoId : costosCambiados) {
                    if (tracker.estaCancelado()) {
                        finalizarCancelado(iniciadoEn);
                        return;
                    }
                    try {
                        recalculoPrecioFacade.recalcularProductoEnTodosLosCanales(productoId);
                    } catch (Exception e) {
                        log.warn("Error recalculando precios para producto {}: {}", productoId, e.getMessage());
                        advertencias.add("Error recalculando producto ID " + productoId + ": " + e.getMessage());
                    }
                }
                log.info("REPOSICIÓN - Recálculo completado");
            }

            // ---- Fase 2: Ventas por período (con cache incremental) ----
            LocalDate hoy = LocalDate.now();
            int diasPorPeriodo = config.getDiasPorPeriodo();
            int diasTotales = diasPorPeriodo * 3;
            LocalDate inicio90Dias = hoy.minusDays(diasTotales);

            LocalDate ultimoVentasFetch = config.getUltimoVentasFetch();

            // Determinar rango a fetchear
            LocalDate fetchDesde;
            if (ultimoVentasFetch == null) {
                fetchDesde = inicio90Dias;
                log.info("REPOSICIÓN - Fase 2: Sin cache, fetch completo desde {}", fetchDesde);
            } else {
                // Re-fetch desde el último día cacheado (inclusive) por si fue incompleto
                fetchDesde = ultimoVentasFetch;
                log.info("REPOSICIÓN - Fase 2: Cache disponible hasta {}, fetch incremental desde {}", ultimoVentasFetch, fetchDesde);
            }

            // Fetch de DUX solo para el rango faltante
            if (!fetchDesde.isAfter(hoy)) {
                actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                        String.format("Obteniendo ventas desde %s hasta %s...", fetchDesde, hoy));

                List<FacturaDux> todasFacturas = new ArrayList<>();
                for (int idSucursal : sucursales) {
                    if (tracker.estaCancelado()) {
                        finalizarCancelado(iniciadoEn);
                        return;
                    }

                    log.info("REPOSICIÓN - Fase 2: Obteniendo facturas ({} a {}) sucursal {}...",
                            fetchDesde, hoy, idSucursal);
                    actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                            String.format("Obteniendo ventas (%s a %s) sucursal %d...",
                                    fetchDesde, hoy, idSucursal));

                    final int sucId = idSucursal;
                    BiConsumer<Integer, Integer> facturasProgress = (page, totalPages) ->
                            actualizarEstado(true, 0, page, 0, 0, "ejecutando", iniciadoEn,
                                    String.format("Obteniendo ventas sucursal %d... página %d", sucId, page));

                    List<FacturaDux> facturas = duxService.obtenerFacturas(
                            fetchDesde.format(fmt), hoy.format(fmt), idEmpresa, idSucursal, tracker.getFlagCancelado(), facturasProgress);
                    todasFacturas.addAll(facturas);
                    log.info("REPOSICIÓN - Fase 2: {} facturas obtenidas de sucursal {}", facturas.size(), idSucursal);
                }

                // Persistir en cache (delete+insert del rango fetched)
                if (!todasFacturas.isEmpty()) {
                    actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                            String.format("Guardando %d facturas en cache...", todasFacturas.size()));
                    self.persistirVentasEnCache(todasFacturas, fetchDesde, hoy);
                }

                log.info("REPOSICIÓN - Fase 2: {} facturas totales cacheadas para rango {} a {}",
                        todasFacturas.size(), fetchDesde, hoy);
            } else {
                log.info("REPOSICIÓN - Fase 2: Cache al día, sin fetch necesario");
            }

            // Limpiar entradas >90 días
            self.limpiarCacheVentas(inicio90Dias);

            // Actualizar config
            configRepository.findById(1).ifPresent(c -> {
                c.setUltimoVentasFetch(hoy);
                configRepository.save(c);
            });

            // Construir ventasPorMes desde cache (3 períodos de N días)
            List<Map<String, Integer>> ventasPorMes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                LocalDate periodoFin = hoy.minusDays(i * diasPorPeriodo);
                LocalDate periodoInicio = hoy.minusDays((i + 1) * diasPorPeriodo - 1);

                Map<String, Integer> ventasMes = new HashMap<>();
                List<Object[]> resultados = ventaDiariaCacheRepository.sumarVentasPorSkuEnRango(periodoInicio, periodoFin);
                for (Object[] row : resultados) {
                    String sku = (String) row[0];
                    long total = ((Number) row[1]).longValue();
                    ventasMes.put(sku, (int) total);
                }
                ventasPorMes.add(ventasMes);
                log.info("REPOSICIÓN - Fase 2: Período {} ({} a {}): {} SKUs con ventas (desde cache)",
                        i + 1, periodoInicio, periodoFin, ventasMes.size());
            }

            if (tracker.estaCancelado()) {
                finalizarCancelado(iniciadoEn);
                return;
            }

            // ---- Fase 3: Pedidos pendientes (clientes) x sucursal ----
            Map<String, Integer> pendienteClientes = new HashMap<>();
            LocalDate desde = hoy.minusDays(180);
            for (int idSucursal : sucursales) {
                if (tracker.estaCancelado()) {
                    finalizarCancelado(iniciadoEn);
                    return;
                }

                log.info("REPOSICIÓN - Fase 3: Obteniendo pedidos pendientes sucursal {}...", idSucursal);
                actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn,
                        String.format("Obteniendo pedidos pendientes sucursal %d...", idSucursal));

                BiConsumer<Integer, Integer> pedidosProgress = (page, totalPages) ->
                        actualizarEstado(true, 0, page, 0, 0, "ejecutando", iniciadoEn,
                                String.format("Obteniendo pedidos pendientes sucursal %d... página %d", idSucursal, page));

                List<PedidoDux> pedidos = duxService.obtenerPedidosPendientes(
                        desde.format(fmt), hoy.format(fmt), idEmpresa, idSucursal, tracker.getFlagCancelado(), pedidosProgress);
                int prevSize = pendienteClientes.size();
                for (PedidoDux pedido : pedidos) {
                    if (pedido.getDetalles() == null) continue;
                    for (PedidoDux.PedidoDetalleDux det : pedido.getDetalles()) {
                        if (det.getCodItem() != null && det.getCtd() != null) {
                            try {
                                int ctd = Integer.parseInt(det.getCtd().replace(",", ".").split("\\.")[0]);
                                pendienteClientes.merge(det.getCodItem().trim(), ctd, Integer::sum);
                            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                                // Cantidad con formato inesperado (ej. "." o vacío): se ignora ese detalle.
                            }
                        }
                    }
                }
                log.info("REPOSICIÓN - Fase 3: {} pedidos obtenidos, {} SKUs con pendientes (sucursal {})",
                        pedidos.size(), pendienteClientes.size() - prevSize, idSucursal);
            }

            if (tracker.estaCancelado()) {
                finalizarCancelado(iniciadoEn);
                return;
            }

            // ---- Fase 4: OC pendientes + última compra (locales) ----
            log.info("REPOSICIÓN - Fase 4: Obteniendo OC pendientes y última compra...");
            actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn, "Obteniendo OC pendientes...");
            Map<Integer, Integer> pendienteProveedores = ordenCompraService.obtenerPendientesPorProducto();
            Map<Integer, OrdenCompraService.UltimaCompra> ultimasCompras = ordenCompraService.obtenerUltimaCompraPorProducto();
            log.info("REPOSICIÓN - Fase 4 completada: {} productos con OC pendientes, {} con última compra",
                    pendienteProveedores.size(), ultimasCompras.size());

            // ---- Fase 5: Calcular sugerencias ----
            log.info("REPOSICIÓN - Fase 5: Calculando sugerencias...");
            actualizarEstado(true, 0, 0, 0, 0, "ejecutando", iniciadoEn, "Calculando sugerencias...");

            List<Producto> productos = productoRepository.findActivosConProveedor().stream()
                    .filter(p -> p.getTagReposicion() != TagReposicion.LIQ)
                    .toList();
            log.info("REPOSICIÓN - Fase 5: {} productos activos a calcular (excl. LIQ)", productos.size());

            BigDecimal peso1 = config.getPesoMes1();
            BigDecimal peso2 = config.getPesoMes2();
            BigDecimal peso3 = config.getPesoMes3();
            int mesesCobertura = config.getMesesCobertura();

            List<SugerenciaReposicionDTO> sugerencias = new ArrayList<>();
            int procesados = 0;

            for (Producto producto : productos) {
                if (tracker.estaCancelado()) {
                    finalizarCancelado(iniciadoEn);
                    return;
                }

                String sku = producto.getSku();
                int stock = producto.getStock() != null ? producto.getStock() : 0;
                int pendClientes = pendienteClientes.getOrDefault(sku, 0);
                int pendProveedores = pendienteProveedores.getOrDefault(producto.getId(), 0);

                int v1 = ventasPorMes.get(0).getOrDefault(sku, 0);
                int v2 = ventasPorMes.get(1).getOrDefault(sku, 0);
                int v3 = ventasPorMes.get(2).getOrDefault(sku, 0);

                double promedio = v1 * peso1.doubleValue()
                        + v2 * peso2.doubleValue()
                        + v3 * peso3.doubleValue();

                // Derivar diario del mismo promedio ponderado (consistente con la estimación de demanda)
                double promDiario = promedio / diasPorPeriodo;

                // Lead time: buffer de stock para cubrir tiempo de entrega del proveedor
                Integer leadTime = producto.getProveedor().getLeadTimeDias();
                double bufferLeadTime = (leadTime != null && leadTime > 0) ? promDiario * leadTime : 0;

                int disponible = stock - pendClientes + pendProveedores;
                int sugerencia = Math.max(0, (int) Math.ceil(promedio * mesesCobertura + bufferLeadTime - disponible));

                // Aplicar MOQ (mínimo de compra)
                if (sugerencia > 0 && producto.getMoq() != null && producto.getMoq() > sugerencia) {
                    sugerencia = producto.getMoq();
                }

                // Redondear al múltiplo de UxB superior
                if (sugerencia > 0 && producto.getUxb() != null && producto.getUxb() > 1) {
                    int uxb = producto.getUxb();
                    sugerencia = (int) Math.ceil((double) sugerencia / uxb) * uxb;
                }

                // Punto de reorden: stock mínimo para cubrir lead time
                int puntoReorden = (leadTime != null && leadTime > 0) ? (int) Math.ceil(promDiario * leadTime) : 0;
                boolean urgente = puntoReorden > 0 && disponible <= puntoReorden;

                promDiario = Math.round(promDiario * 100.0) / 100.0;

                OrdenCompraService.UltimaCompra uc = ultimasCompras.get(producto.getId());

                sugerencias.add(new SugerenciaReposicionDTO(
                        producto.getId(),
                        sku,
                        producto.getCodExt(),
                        producto.getDescripcion(),
                        producto.getProveedor().getApodo(),
                        producto.getUxb(),
                        producto.getMoq(),
                        producto.getTagReposicion(),
                        stock,
                        pendClientes,
                        pendProveedores,
                        disponible,
                        v1, v2, v3,
                        Math.round(promedio * 100.0) / 100.0,
                        promDiario,
                        puntoReorden,
                        urgente,
                        sugerencia,
                        sugerencia, // pedido = sugerencia por defecto
                        uc != null ? uc.fecha() : null,
                        uc != null ? uc.cantidad() : 0
                ));

                procesados++;
                if (procesados % 100 == 0) {
                    actualizarEstado(true, productos.size(), procesados, 0, 0,
                            "ejecutando", iniciadoEn,
                            String.format("Calculando sugerencias %d/%d", procesados, productos.size()));
                }
            }

            // Ordenar: urgentes primero, luego PRIO, luego con sugerencia > 0, luego por proveedor
            sugerencias.sort(Comparator
                    .<SugerenciaReposicionDTO, Integer>comparing(s -> s.urgente() ? 0 : 1)
                    .thenComparing(s -> s.tagReposicion() == TagReposicion.PRIO ? 0 : 1)
                    .thenComparing(s -> s.sugerencia() > 0 ? 0 : 1)
                    .thenComparing(s -> s.proveedorNombre() != null ? s.proveedorNombre() : "")
                    .thenComparing(SugerenciaReposicionDTO::sku));

            int conSugerencia = (int) sugerencias.stream().filter(s -> s.sugerencia() > 0).count();

            resultadoCalculo = new ReposicionResultDTO(
                    sugerencias,
                    productos.size(),
                    conSugerencia,
                    advertencias
            );

            tracker.completar(productos.size(), conSugerencia, 0,
                    String.format("Completado. %d productos, %d con sugerencia", productos.size(), conSugerencia));

            log.info("REPOSICIÓN - Cálculo completado. {} productos, {} con sugerencia",
                    productos.size(), conSugerencia);

        } catch (Exception e) {
            log.error("REPOSICIÓN - Error fatal: {}", e.getMessage(), e);
            tracker.completarConError(e.getMessage());
        } finally {
            duxService.getRetryHandler().clearRetryListener();
            tracker.liberar();
        }
    }

    // =====================================================
    // EXCEL EXPORT
    // =====================================================

    @Override
    public ExcelResult generarExcelSugerencias() {
        if (resultadoCalculo == null) {
            throw new BadRequestException("No hay resultado de cálculo disponible. Ejecute POST /api/reposicion/calcular primero");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // Agrupar por proveedor
            Map<String, List<SugerenciaReposicionDTO>> porProveedor = resultadoCalculo.sugerencias().stream()
                    .filter(s -> s.pedido() > 0)
                    .collect(Collectors.groupingBy(
                            s -> s.proveedorNombre() != null ? s.proveedorNombre() : "SIN PROVEEDOR",
                            LinkedHashMap::new,
                            Collectors.toList()));

            // Estilos
            CellStyle headerStyle = crearEstiloHeader(workbook);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle centeredStyle = workbook.createCellStyle();
            centeredStyle.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(centeredStyle);

            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            numStyle.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(numStyle);

            for (Map.Entry<String, List<SugerenciaReposicionDTO>> entry : porProveedor.entrySet()) {
                String provNombre = entry.getKey();
                List<SugerenciaReposicionDTO> items = entry.getValue();

                // Nombre de hoja: máx 31 chars, sin caracteres inválidos
                String sheetName = provNombre.length() > 31 ? provNombre.substring(0, 31) : provNombre;
                sheetName = sheetName.replaceAll("[\\[\\]*/\\\\?:]", "_");
                Sheet sheet = workbook.createSheet(sheetName);

                // Header
                String[] columnas = {"SKU", "CODEXT", "PRODUCTO", "UxB", "STOCK", "DEBO", "OC PEND", "SALDO", "1M", "2M", "3M", "PEDIDO"};
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columnas.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnas[i]);
                    cell.setCellStyle(headerStyle);
                }

                // Datos
                int rowNum = 1;
                for (SugerenciaReposicionDTO s : items) {
                    Row row = sheet.createRow(rowNum++);
                    Cell skuCell = row.createCell(0);
                    skuCell.setCellValue(s.sku() != null ? s.sku() : "");
                    skuCell.setCellStyle(centeredStyle);
                    Cell codExtCell = row.createCell(1);
                    codExtCell.setCellValue(s.codExt() != null ? s.codExt() : "");
                    codExtCell.setCellStyle(centeredStyle);
                    Cell descCell = row.createCell(2);
                    descCell.setCellValue(s.descripcion() != null ? s.descripcion() : "");
                    descCell.setCellStyle(centeredStyle);

                    Cell uxbCell = row.createCell(3);
                    if (s.uxb() != null) {
                        uxbCell.setCellValue(s.uxb());
                    }
                    uxbCell.setCellStyle(numStyle);

                    crearCeldaNum(row, 4, s.stockActual(), numStyle);
                    crearCeldaNum(row, 5, s.pendienteClientes(), numStyle);
                    crearCeldaNum(row, 6, s.pendienteProveedores(), numStyle);
                    crearCeldaNum(row, 7, s.saldoDisponible(), numStyle);
                    crearCeldaNum(row, 8, s.ventasMes1(), numStyle);
                    crearCeldaNum(row, 9, s.ventasMes2(), numStyle);
                    crearCeldaNum(row, 10, s.ventasMes3(), numStyle);
                    crearCeldaNum(row, 11, s.pedido(), numStyle);
                }

                // Freeze pane y autosize
                sheet.createFreezePane(0, 1);
                for (int i = 0; i < columnas.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            // Hoja resumen
            Sheet resumen = workbook.createSheet("RESUMEN");
            Row resHeaderRow = resumen.createRow(0);
            String[] resCols = {"PROVEEDOR", "PRODUCTOS", "TOTAL UNIDADES"};
            for (int i = 0; i < resCols.length; i++) {
                Cell cell = resHeaderRow.createCell(i);
                cell.setCellValue(resCols[i]);
                cell.setCellStyle(headerStyle);
            }
            int resRowNum = 1;
            for (Map.Entry<String, List<SugerenciaReposicionDTO>> entry : porProveedor.entrySet()) {
                Row row = resumen.createRow(resRowNum++);
                Cell provCell = row.createCell(0);
                provCell.setCellValue(entry.getKey());
                provCell.setCellStyle(centeredStyle);
                Cell cantCell = row.createCell(1);
                cantCell.setCellValue(entry.getValue().size());
                cantCell.setCellStyle(numStyle);
                int totalUnidades = entry.getValue().stream().mapToInt(SugerenciaReposicionDTO::pedido).sum();
                Cell totalCell = row.createCell(2);
                totalCell.setCellValue(totalUnidades);
                totalCell.setCellStyle(numStyle);
            }
            for (int i = 0; i < resCols.length; i++) resumen.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            String fecha = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String filename = "SUGERENCIAS_REPOSICION_" + fecha + ".xlsx";

            return new ExcelResult(out.toByteArray(), filename);

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de sugerencias: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExcelResult generarExcelOrdenCompra(Integer ordenCompraId) {
        OrdenCompra oc = ordenCompraRepository.findById(ordenCompraId)
                .orElseThrow(() -> new NotFoundException("Orden de compra no encontrada"));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Orden de Compra");

            CellStyle headerStyle = crearEstiloHeader(workbook);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle centeredStyle = workbook.createCellStyle();
            centeredStyle.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(centeredStyle);

            CellStyle numStyle = workbook.createCellStyle();
            numStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            numStyle.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(numStyle);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.CENTER);
            aplicarBordes(moneyStyle);

            String[] columnas = {"SKU", "CODEXT", "PRODUCTO", "UxB", "CANTIDAD", "COSTO UNIT", "SUBTOTAL"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            BigDecimal totalGeneral = BigDecimal.ZERO;

            for (OrdenCompraLinea linea : oc.getLineas()) {
                Producto prod = linea.getProducto();
                Row row = sheet.createRow(rowNum++);
                Cell skuCell = row.createCell(0);
                skuCell.setCellValue(prod.getSku() != null ? prod.getSku() : "");
                skuCell.setCellStyle(centeredStyle);
                Cell codExtCell = row.createCell(1);
                codExtCell.setCellValue(prod.getCodExt() != null ? prod.getCodExt() : "");
                codExtCell.setCellStyle(centeredStyle);
                Cell descCell = row.createCell(2);
                descCell.setCellValue(prod.getDescripcion() != null ? prod.getDescripcion() : "");
                descCell.setCellStyle(centeredStyle);

                Cell uxbCell = row.createCell(3);
                if (prod.getUxb() != null) {
                    uxbCell.setCellValue(prod.getUxb());
                }
                uxbCell.setCellStyle(numStyle);

                Cell cantCell = row.createCell(4);
                cantCell.setCellValue(linea.getCantidadPedida());
                cantCell.setCellStyle(numStyle);

                if (linea.getCostoUnitario() != null) {
                    Cell costoCell = row.createCell(5);
                    costoCell.setCellValue(linea.getCostoUnitario().doubleValue());
                    costoCell.setCellStyle(moneyStyle);

                    BigDecimal subtotal = linea.getCostoUnitario().multiply(BigDecimal.valueOf(linea.getCantidadPedida()));
                    Cell subtotalCell = row.createCell(6);
                    subtotalCell.setCellValue(subtotal.doubleValue());
                    subtotalCell.setCellStyle(moneyStyle);
                    totalGeneral = totalGeneral.add(subtotal);
                }
            }

            // Footer con total
            Row totalRow = sheet.createRow(rowNum + 1);
            Cell labelCell = totalRow.createCell(5);
            labelCell.setCellValue("TOTAL:");
            labelCell.setCellStyle(headerStyle);
            Cell totalCell = totalRow.createCell(6);
            totalCell.setCellValue(totalGeneral.doubleValue());
            totalCell.setCellStyle(moneyStyle);

            sheet.createFreezePane(0, 1);
            for (int i = 0; i < columnas.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            // Nombre: ORDEN_COMPRA_PROVEEDOR_fechaHora.xlsx
            String provNombre = oc.getProveedor().getApodo() != null
                    ? oc.getProveedor().getApodo() : "SIN_PROVEEDOR";
            provNombre = provNombre.replaceAll("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑ _-]", "").replace(" ", "_");
            String fecha = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            String filename = "ORDEN_COMPRA_" + provNombre + "_" + fecha + ".xlsx";

            return new ExcelResult(out.toByteArray(), filename);

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de OC: " + e.getMessage(), e);
        }
    }

    // =====================================================
    // AJUSTAR PEDIDOS
    // =====================================================

    @Override
    public ReposicionResultDTO ajustarPedidos(AjustePedidoDTO dto) {
        if (resultadoCalculo == null) {
            throw new BadRequestException("No hay resultado de cálculo disponible. Ejecute el cálculo primero");
        }

        Map<Integer, Integer> ajustes = new HashMap<>();
        for (AjustePedidoDTO.LineaAjusteDTO ajuste : dto.ajustes()) {
            ajustes.put(ajuste.productoId(), ajuste.pedido());
        }

        List<SugerenciaReposicionDTO> actualizadas = resultadoCalculo.sugerencias().stream()
                .map(s -> {
                    Integer nuevoPedido = ajustes.get(s.productoId());
                    if (nuevoPedido != null) {
                        return new SugerenciaReposicionDTO(
                                s.productoId(), s.sku(), s.codExt(), s.descripcion(),
                                s.proveedorNombre(), s.uxb(), s.moq(), s.tagReposicion(),
                                s.stockActual(), s.pendienteClientes(), s.pendienteProveedores(),
                                s.saldoDisponible(), s.ventasMes1(), s.ventasMes2(), s.ventasMes3(),
                                s.promedioVentas(), s.promedioDiario(), s.puntoReorden(), s.urgente(),
                                s.sugerencia(), nuevoPedido,
                                s.ultimaCompraFecha(), s.ultimaCompraCantidad());
                    }
                    return s;
                })
                .toList();

        resultadoCalculo = new ReposicionResultDTO(
                actualizadas,
                resultadoCalculo.totalProductos(),
                resultadoCalculo.productosConSugerencia(),
                resultadoCalculo.advertencias());

        auditoriaService.registrarCambios(
                AuditoriaEntidad.REPOSICION,
                1,
                "RESULTADO",
                AuditoriaAccion.UPDATE,
                Map.of(),
                Map.of("ajustesPedidos", dto.ajustes().size() + " producto(s) ajustado(s)")
        );

        return resultadoCalculo;
    }

    // =====================================================
    // GENERAR OC DESDE RESULTADO
    // =====================================================

    @Override
    @Transactional
    public List<OrdenCompraDTO> generarOrdenesDesdeResultado(Integer proveedorId) {
        if (resultadoCalculo == null) {
            throw new BadRequestException("No hay resultado de cálculo disponible. Ejecute el cálculo primero");
        }

        // Usar pedido (ajustado por usuario) en vez de sugerencia
        List<SugerenciaReposicionDTO> conPedido = resultadoCalculo.sugerencias().stream()
                .filter(s -> s.pedido() > 0)
                .toList();

        if (conPedido.isEmpty()) {
            return List.of();
        }

        // Cargar productos para obtener proveedorId y costo
        List<Integer> productoIds = conPedido.stream()
                .map(SugerenciaReposicionDTO::productoId)
                .toList();
        Map<Integer, Producto> productosMap = productoRepository.findAllById(productoIds).stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // Agrupar por proveedor
        Map<Integer, List<SugerenciaReposicionDTO>> porProveedor = new LinkedHashMap<>();
        for (SugerenciaReposicionDTO s : conPedido) {
            Producto prod = productosMap.get(s.productoId());
            if (prod != null && prod.getProveedor() != null) {
                // Filtrar por proveedor si se especificó
                if (proveedorId != null && !prod.getProveedor().getId().equals(proveedorId)) {
                    continue;
                }
                porProveedor.computeIfAbsent(prod.getProveedor().getId(), k -> new ArrayList<>()).add(s);
            }
        }

        // Verificar si ya existen BORRADOR para estos proveedores
        List<String> proveedoresConBorrador = new ArrayList<>();
        for (Integer provId : porProveedor.keySet()) {
            if (ordenCompraRepository.existsByProveedorIdAndEstado(provId, EstadoOrdenCompra.BORRADOR)) {
                Producto cualquiera = productosMap.values().stream()
                        .filter(p -> p.getProveedor() != null && p.getProveedor().getId().equals(provId))
                        .findFirst().orElse(null);
                String nombre = cualquiera != null ? cualquiera.getProveedor().getApodo() : "ID " + provId;
                proveedoresConBorrador.add(nombre);
            }
        }
        if (!proveedoresConBorrador.isEmpty()) {
            throw new BadRequestException(
                    "Ya existen órdenes en BORRADOR para: " + String.join(", ", proveedoresConBorrador)
                            + ". Elimínelas o envíelas antes de generar nuevas");
        }

        // Crear una OC por proveedor
        List<OrdenCompraDTO> creadas = new ArrayList<>();
        for (Map.Entry<Integer, List<SugerenciaReposicionDTO>> entry : porProveedor.entrySet()) {
            List<OrdenCompraLineaCreateDTO> lineas = entry.getValue().stream()
                    .map(s -> new OrdenCompraLineaCreateDTO(
                            s.productoId(),
                            s.pedido(),
                            productosMap.get(s.productoId()).getCosto()))
                    .toList();

            OrdenCompraCreateDTO dto = new OrdenCompraCreateDTO(entry.getKey(), null, lineas);
            creadas.add(ordenCompraService.crear(dto));
        }

        auditoriaService.registrarCambios(
                AuditoriaEntidad.REPOSICION,
                1,
                "ORDENES",
                AuditoriaAccion.UPDATE,
                Map.of(),
                Map.of(
                        "ordenesGeneradas", creadas.size() + " orden(es)",
                        "proveedorFiltro", proveedorId == null ? "Todos" : String.valueOf(proveedorId)
                )
        );

        log.info("REPOSICIÓN - Generadas {} órdenes de compra desde sugerencias", creadas.size());
        return creadas;
    }

    // =====================================================
    // PERSISTIR DATOS DUX (stock + costo)
    // =====================================================

    @Override
    @Transactional
    public List<Integer> persistirDatosDux(Map<String, DuxItemData> dataMap) {
        return ejecutarPersistirDatosDux(dataMap);
    }

    private List<Integer> ejecutarPersistirDatosDux(Map<String, DuxItemData> dataMap) {
        // Cargar productos actuales para comparar costos
        Map<String, Producto> productosBySku = productoRepository.findBySkuIn(new ArrayList<>(dataMap.keySet())).stream()
                .filter(p -> p.getSku() != null)
                .collect(Collectors.toMap(Producto::getSku, p -> p, (a, b) -> a));

        List<Integer> costosCambiados = new ArrayList<>();
        int stockActualizados = 0;
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));

        for (Map.Entry<String, DuxItemData> entry : dataMap.entrySet()) {
            String sku = entry.getKey();
            DuxItemData data = entry.getValue();
            Producto producto = productosBySku.get(sku);
            if (producto == null) continue;

            BigDecimal nuevoCosto = data.costo();
            boolean costoCambio = nuevoCosto != null
                    && (producto.getCosto() == null || producto.getCosto().compareTo(nuevoCosto) != 0);

            if (costoCambio) {
                productoRepository.updateStockAndCostoBySku(sku, data.stock(), nuevoCosto, ahora);
                costosCambiados.add(producto.getId());
            } else {
                productoRepository.updateStockBySku(sku, data.stock());
            }
            stockActualizados++;
        }

        log.info("REPOSICIÓN - Datos persistidos: {}/{} SKUs, {} con cambio de costo",
                stockActualizados, dataMap.size(), costosCambiados.size());
        return costosCambiados;
    }

    // =====================================================
    // CACHE DE VENTAS DIARIAS
    // =====================================================

    @Override
    @Transactional
    public void persistirVentasEnCache(List<FacturaDux> facturas, LocalDate fechaDesde, LocalDate fechaHasta) {
        ejecutarPersistirVentasEnCache(facturas, fechaDesde, fechaHasta);
    }

    private void ejecutarPersistirVentasEnCache(List<FacturaDux> facturas, LocalDate fechaDesde, LocalDate fechaHasta) {
        // Agregar ventas por (sku, fecha) - notas de crédito restan
        Map<String, Map<LocalDate, Integer>> ventasPorSkuFecha = new HashMap<>();
        for (FacturaDux factura : facturas) {
            if (factura.getDetalles() == null || factura.getFecha() == null) continue;
            LocalDate fecha = parseFechaDux(factura.getFecha());
            if (fecha == null) continue;

            boolean esNotaCredito = "NOTA_CREDITO".equals(factura.getTipoComp());

            for (FacturaDux.FacturaDetalleDux det : factura.getDetalles()) {
                if (det.getCodItem() != null && det.getCtd() != null) {
                    try {
                        int ctd = Integer.parseInt(det.getCtd().replace(",", ".").split("\\.")[0]);
                        int cantidad = esNotaCredito ? -Math.abs(ctd) : Math.abs(ctd);
                        ventasPorSkuFecha
                                .computeIfAbsent(det.getCodItem().trim(), k -> new HashMap<>())
                                .merge(fecha, cantidad, Integer::sum);
                    } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
                        // Cantidad con formato inesperado (ej. "." o vacío): se ignora ese detalle.
                    }
                }
            }
        }

        // Delete del rango y re-insert
        ventaDiariaCacheRepository.eliminarEnRango(fechaDesde, fechaHasta);

        List<VentaDiariaCache> entradas = new ArrayList<>();
        for (Map.Entry<String, Map<LocalDate, Integer>> skuEntry : ventasPorSkuFecha.entrySet()) {
            for (Map.Entry<LocalDate, Integer> fechaEntry : skuEntry.getValue().entrySet()) {
                int cantidad = Math.max(0, fechaEntry.getValue());
                if (cantidad > 0) {
                    entradas.add(new VentaDiariaCache(skuEntry.getKey(), fechaEntry.getKey(), cantidad));
                }
            }
        }

        ventaDiariaCacheRepository.saveAll(entradas);
        log.info("REPOSICIÓN - Cache: {} entradas guardadas para rango {} a {}",
                entradas.size(), fechaDesde, fechaHasta);
    }

    @Override
    @Transactional
    public void invalidarCacheVentas() {
        ventaDiariaCacheRepository.eliminarTodo();
        log.info("REPOSICIÓN - Cache de ventas invalidado (truncado)");
    }

    @Override
    @Transactional
    public void limpiarCacheVentas(LocalDate cutoff) {
        ventaDiariaCacheRepository.eliminarAnterioresA(cutoff);
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private final Set<String> fechasNoParseadasLogueadas = new HashSet<>();

    private LocalDate parseFechaDux(String fechaStr) {
        if (fechaStr == null || fechaStr.isBlank()) return null;
        for (DateTimeFormatter fmt : FORMATOS_FECHA_DUX) {
            try {
                return LocalDateTime.parse(fechaStr.contains("T")
                        ? (fechaStr.length() > 19 ? fechaStr.substring(0, 23) : fechaStr.substring(0, 19))
                        : fechaStr, fmt).toLocalDate();
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(fechaStr, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        if (fechasNoParseadasLogueadas.add(fechaStr)) {
            log.warn("REPOSICIÓN - No se pudo parsear fecha DUX: {}", fechaStr);
        }
        return null;
    }

    private void finalizarCancelado(LocalDateTime iniciadoEn) {
        // tracker.completar() detecta estaCancelado() y graba estado="cancelado".
        tracker.completar(0, 0, 0, "Cálculo cancelado por el usuario");
    }

    private void actualizarEstado(boolean enEjecucion, int total, int procesados,
                                  int exitosos, int errores, String estado,
                                  LocalDateTime iniciadoEn, String mensaje) {
        // El parámetro `iniciadoEn` se mantiene por compatibilidad pero ya no se usa
        // (el tracker preserva su propio iniciadoEn). El parámetro `estado` también se
        // ignora: el tracker decide entre "ejecutando" / "completado" / "cancelado".
        if (enEjecucion) {
            tracker.actualizar(total, procesados, exitosos, errores, mensaje);
        } else {
            tracker.completar(total, exitosos, errores, mensaje);
        }
    }

    private CellStyle crearEstiloHeader(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        aplicarBordes(style);
        return style;
    }

    private void aplicarBordes(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private void crearCeldaNum(Row row, int col, int valor, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(valor);
        cell.setCellStyle(style);
    }

    private Map<String, String> capturarSnapshotConfig(ReposicionConfig config) {
        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("mesesCobertura", normalizar(config.getMesesCobertura()));
        snapshot.put("diasPorPeriodo", normalizar(config.getDiasPorPeriodo()));
        snapshot.put("pesoMes1", normalizar(config.getPesoMes1()));
        snapshot.put("pesoMes2", normalizar(config.getPesoMes2()));
        snapshot.put("pesoMes3", normalizar(config.getPesoMes3()));
        snapshot.put("idEmpresaDux", normalizar(config.getIdEmpresaDux()));
        snapshot.put("idsSucursalDux", normalizarLista(config.getIdsSucursalDux()));
        return snapshot;
    }

    private void registrarEventoProceso(String campo, String mensaje) {
        auditoriaService.registrarCambios(
                AuditoriaEntidad.REPOSICION,
                1,
                "PROCESO",
                AuditoriaAccion.UPDATE,
                Map.of(),
                Map.of(campo, mensaje)
        );
    }

    private String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizarLista(List<Integer> values) {
        return values == null || values.isEmpty()
                ? null
                : values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}

