package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import ar.com.leo.super_master_backend.dominio.producto.calculo.service.RecalculoPrecioFacade;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoMargenRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Tracker de precios que requieren recálculo. Persiste el estado en BD para sobrevivir
 * reinicios y poder mostrar "precio desactualizado" por fila en el frontend.
 *
 * <p>Dos fuentes de obsolescencia:
 * <ul>
 *   <li>{@code producto_canal_precios.obsoleto = TRUE} — un precio existente que está
 *       desactualizado. El aplicador recalcula esa combinación (producto, canal).</li>
 *   <li>{@code canales.requiere_reevaluar_catalogo = TRUE} — cambio en el canal que puede
 *       agregar/quitar productos (regla, concepto, cuota, descuento, canal base).
 *       El aplicador itera TODO el catálogo contra ese canal.</li>
 * </ul>
 *
 * <p>La API pública se mantiene igual que la versión legacy (in-memory) para no impactar
 * a los ~14 services que la consumen. Internamente, cada {@code marcarX(...)} se traduce
 * a un UPDATE bulk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecalculoPendienteService {

    private final ProductoCanalPrecioRepository pcpRepository;
    private final CanalRepository canalRepository;
    private final ProductoRepository productoRepository;
    private final ProductoMargenRepository productoMargenRepository;

    @Autowired
    @Lazy
    private RecalculoPendienteSseService sseService;

    /**
     * Inyección @Lazy para romper la dependencia circular:
     * RecalculoPrecioFacade → RecalculoPendienteService → RecalculoPrecioFacade.
     * Solo se usa desde {@link #marcarProductoOCalcularInicial} para disparar el cálculo
     * sincrónico cuando el producto aún no tiene filas en producto_canal_precios.
     */
    @Autowired
    @Lazy
    private RecalculoPrecioFacade recalculoPrecioFacade;

    /**
     * Marca un cambio que afecta a TODOS los productos en TODOS los canales. Usado por el
     * fallback de seguridad cuando no se sabe el scope. La consecuencia es severa: el
     * aplicador hará un recálculo masivo completo.
     */
    @Transactional
    public void marcarTodo(String motivo) {
        int filas = pcpRepository.marcarTodoObsoleto(motivo);
        int canales = canalRepository.marcarTodosRequiereReevaluar(motivo);
        log.debug("Marcar TODO obsoleto [{}]: {} filas, {} canales", motivo, filas, canales);
        broadcast();
    }

    /**
     * Marca obsoletos los precios de un producto en todos los canales donde tiene
     * fila en {@code producto_canal_precios}.
     */
    @Transactional
    public void marcarProducto(String motivo, Integer productoId) {
        if (productoId == null) return;
        marcarProductos(motivo, List.of(productoId));
    }

    /**
     * Marca obsoletos los precios de varios productos. Más eficiente que llamar
     * {@link #marcarProducto} N veces: un solo UPDATE y un solo broadcast.
     */
    @Transactional
    public void marcarProductos(String motivo, Iterable<Integer> productoIds) {
        List<Integer> ids = sanitize(productoIds);
        if (ids.isEmpty()) return;
        int filas = pcpRepository.marcarObsoletoPorProductos(ids, motivo);
        log.debug("Marcar {} productos obsoletos [{}]: {} filas afectadas", ids.size(), motivo, filas);
        broadcast();
    }

    /**
     * Como {@link #marcarProducto}, pero si el producto NO tiene filas en
     * {@code producto_canal_precios} (UPDATE afectaría 0 filas y el banner quedaría
     * vacío), dispara el recálculo sincrónico para crear las filas iniciales.
     *
     * <p>Útil cuando el cambio que se notifica es lo que finalmente desbloquea el
     * cálculo del producto (ej.: primer margen, primer costo). Si al producto le
     * faltan datos para calcular (costo, iva o margen), cae al {@code marcarProducto}
     * normal — los datos faltantes impedirían el cálculo de todos modos.
     *
     * <p>El recálculo síncrono se ejecuta en la misma transacción del caller: si tira
     * excepción, todo se rollbackea. El check de datos mínimos previene los casos más
     * comunes de fallo (sin costo/iva/margen).
     */
    @Transactional
    public void marcarProductoOCalcularInicial(String motivo, Integer productoId) {
        if (productoId == null) return;
        if (productoSinPreciosYConDatosMinimos(productoId)) {
            recalculoPrecioFacade.recalcularProductoEnTodosLosCanales(productoId);
        } else {
            marcarProductos(motivo, List.of(productoId));
        }
    }

    private boolean productoSinPreciosYConDatosMinimos(Integer productoId) {
        if (pcpRepository.existsByProductoId(productoId)) {
            return false; // ya tiene precios → flujo normal de banner
        }
        if (!productoMargenRepository.existsByProductoId(productoId)) {
            return false; // sin margen no se puede calcular
        }
        return productoRepository.findById(productoId)
                .filter(p -> p.getCosto() != null
                        && p.getCosto().compareTo(BigDecimal.ZERO) != 0
                        && p.getIva() != null)
                .isPresent();
    }

    /**
     * Marca un canal para reevaluación de catálogo + obsoletos sus precios actuales.
     * Es el caso típico cuando cambia algo del canal que puede agregar/quitar productos
     * (regla, concepto, cuota, descuento, canal base).
     */
    @Transactional
    public void marcarCanal(String motivo, Integer canalId) {
        if (canalId == null) return;
        marcarCanales(motivo, List.of(canalId));
    }

    /**
     * Marca varios canales para reevaluación del catálogo. Un solo UPDATE y un solo
     * broadcast.
     */
    @Transactional
    public void marcarCanales(String motivo, Iterable<Integer> canalIds) {
        List<Integer> ids = sanitize(canalIds);
        if (ids.isEmpty()) return;
        int canales = canalRepository.marcarRequiereReevaluarPorIds(ids, motivo);
        int filas = pcpRepository.marcarObsoletoPorCanales(ids, motivo);
        log.debug("Marcar {} canales para reevaluar [{}]: {} canales, {} filas obsoletas",
                ids.size(), motivo, canales, filas);
        broadcast();
    }

    /**
     * Plan de recálculo a ejecutar. Se construye on-demand leyendo BD para tener
     * una foto coherente del estado actual.
     */
    public record PlanRecalculo(boolean recalcularTodo, Set<Integer> productos, Set<Integer> canales) {
        public boolean estaVacio() {
            return !recalcularTodo && productos.isEmpty() && canales.isEmpty();
        }
    }

    /**
     * Construye el plan leyendo BD. Si todos los canales activos requieren reevaluar,
     * se interpreta como "recalcular todo". Si solo algunos, se devuelven en {@code canales}.
     * Los productos obsoletos cuyo canal NO está en el set anterior se devuelven en {@code productos}.
     */
    @Transactional(readOnly = true)
    public PlanRecalculo plan() {
        List<Integer> canalesReevaluar = canalRepository.findIdsRequierenReevaluar();
        long canalesTotal = canalRepository.count();
        boolean todo = !canalesReevaluar.isEmpty() && canalesReevaluar.size() == canalesTotal;
        if (todo) {
            return new PlanRecalculo(true, Set.of(), Set.of());
        }
        List<Integer> productos = pcpRepository.findDistinctProductoIdsObsoletos();
        return new PlanRecalculo(false, Set.copyOf(productos), Set.copyOf(canalesReevaluar));
    }

    /**
     * Desmarca el flag {@code requiere_reevaluar_catalogo} del canal y los flags
     * {@code obsoleto} de sus precios. Usado tras un recálculo de canal completo exitoso.
     * Va en su propia transacción porque el caller (recalculoCanalCompletoAsync) corre
     * en un thread @Async sin transacción abierta.
     */
    @Transactional
    public void desmarcarCanalCompletado(Integer canalId) {
        if (canalId == null) return;
        canalRepository.desmarcarRequiereReevaluarPorIds(List.of(canalId));
        pcpRepository.desmarcarObsoletoPorCanal(canalId);
        broadcast();
    }

    /**
     * Desmarca los flags {@code obsoleto} de los precios de un producto tras un
     * recálculo exitoso, y notifica al frontend vía SSE para que el banner se
     * actualice en tiempo real (el usuario ve el contador bajar a medida que el
     * bulk executor procesa).
     *
     * <p>Análogo a {@link #desmarcarCanalCompletado} para canales completos.
     */
    @Transactional
    public void desmarcarProductoCompletado(Integer productoId) {
        if (productoId == null) return;
        pcpRepository.desmarcarObsoletoPorProducto(productoId);
        broadcast();
    }

    /**
     * Limpia todos los marcadores. Usado por el controller al despachar el Apply
     * para que nuevos cambios queden registrados para el siguiente ciclo.
     */
    @Transactional
    public void limpiar() {
        int filas = pcpRepository.desmarcarTodosObsoletos();
        int canales = canalRepository.desmarcarTodosRequiereReevaluar();
        if (filas > 0 || canales > 0) {
            log.info("Recálculo pendiente limpiado ({} filas, {} canales)", filas, canales);
        }
        broadcast();
    }

    /**
     * Snapshot actual del estado para el banner del frontend. Construido on-demand
     * con queries de agregación; barato porque el conteo va por índice {@code idx_pcp_obsoleto}.
     */
    @Transactional(readOnly = true)
    public RecalculoPendienteDTO estado() {
        List<Integer> productoIds = pcpRepository.findDistinctProductoIdsObsoletos();
        List<Integer> canalIdsReevaluar = canalRepository.findIdsRequierenReevaluar();
        long canalesTotal = canalRepository.count();
        boolean todo = !canalIdsReevaluar.isEmpty() && canalIdsReevaluar.size() == canalesTotal;

        int productosCount = productoIds.size();
        int canalesCount = canalIdsReevaluar.size();
        // Cuando es "recalcular todo", el scope es UNO (el recálculo masivo), no
        // se suman productos/canales individuales: el frontend muestra "recálculo
        // masivo pendiente" en lugar del detalle por entidad.
        int totalScope = todo ? 1 : (productosCount + canalesCount);
        if (totalScope == 0) {
            return RecalculoPendienteDTO.vacio();
        }

        List<RecalculoPendienteDTO.MotivoPendiente> motivos = new ArrayList<>();
        for (Object[] row : pcpRepository.resumenObsoletosPorMotivo()) {
            motivos.add(new RecalculoPendienteDTO.MotivoPendiente(
                    (String) row[0], ((Number) row[1]).intValue(), (LocalDateTime) row[2]));
        }
        for (Object[] row : canalRepository.resumenReevaluarPorMotivo()) {
            motivos.add(new RecalculoPendienteDTO.MotivoPendiente(
                    (String) row[0], ((Number) row[1]).intValue(), (LocalDateTime) row[2]));
        }
        motivos.sort((a, b) -> b.cantidad() - a.cantidad());

        LocalDateTime ultima = motivos.stream()
                .map(RecalculoPendienteDTO.MotivoPendiente::ultimoCambio)
                .filter(d -> d != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new RecalculoPendienteDTO(
                true, totalScope, todo, productosCount, canalesCount,
                productoIds, canalIdsReevaluar, ultima, motivos);
    }

    /**
     * Emite por SSE el snapshot actual del estado del banner. Útil para callers que
     * modificaron {@code producto_canal_precios} o {@code canales} por fuera de los
     * métodos {@code marcar*}/{@code desmarcar*} de este service (ej.: DELETE de cuotas
     * que borra filas obsoletas asociadas, o cualquier otra mutación que pueda dejar
     * el conteo del banner desactualizado).
     */
    public void notificarCambioEstado() {
        broadcast();
    }

    private void broadcast() {
        if (sseService == null) return;
        try {
            sseService.broadcast(estado());
        } catch (Exception e) {
            log.warn("Error al broadcast de recálculo pendiente por SSE: {}", e.getMessage());
        }
    }

    private static List<Integer> sanitize(Iterable<Integer> ids) {
        return StreamSupport.stream(ids.spliterator(), false)
                .filter(Objects::nonNull)
                .toList();
    }
}
