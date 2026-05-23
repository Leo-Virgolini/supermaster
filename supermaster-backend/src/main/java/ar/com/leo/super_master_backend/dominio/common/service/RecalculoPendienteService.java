package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    @Autowired
    @Lazy
    private RecalculoPendienteSseService sseService;

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
        int totalScope = (todo ? 1 : 0) + productosCount + canalesCount;
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

    private void broadcast() {
        if (sseService == null) return;
        try {
            sseService.broadcast(estado());
        } catch (Exception e) {
            log.warn("Error al broadcast de recálculo pendiente por SSE: {}", e.getMessage());
        }
    }

    private static List<Integer> sanitize(Iterable<Integer> ids) {
        List<Integer> out = new ArrayList<>();
        for (Integer id : ids) {
            if (id != null) out.add(id);
        }
        return out;
    }
}
