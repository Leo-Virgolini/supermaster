package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracker en memoria de cambios que requieren recálculo de precios.
 *
 * Mantiene el SCOPE de cada cambio (producto/canal/todo) para que al aplicar el
 * recálculo se ejecute el plan más eficiente: si solo se tocó un producto, recalcula
 * ese producto; si solo un canal, ese canal; si algo amplio (ej: concepto), todo.
 *
 * Estado en memoria: si se reinicia el servidor el flag se pierde. Aceptable.
 */
@Slf4j
@Service
public class RecalculoPendienteService {

    private final AtomicInteger cantidad = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> ultimaModificacion = new AtomicReference<>(null);
    // Contador por motivo (descripción del trigger) para mostrar el detalle al usuario.
    private final ConcurrentHashMap<String, AtomicInteger> contadorPorMotivo = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<LocalDateTime>> fechaPorMotivo = new ConcurrentHashMap<>();

    // Scopes: qué hay que recalcular cuando el usuario aprete "Aplicar".
    private final AtomicBoolean recalcularTodo = new AtomicBoolean(false);
    private final Set<Integer> productosPendientes = ConcurrentHashMap.newKeySet();
    private final Set<Integer> canalesPendientes = ConcurrentHashMap.newKeySet();

    // Snapshot del estado actualizado dentro del synchronized en cada mutación.
    // Permite que estado() y broadcast() lean sin tomar el lock — evita que un
    // cliente SSE lento o un endpoint /estado lento bloqueen a marcar*.
    private final AtomicReference<RecalculoPendienteDTO> snapshot = new AtomicReference<>(RecalculoPendienteDTO.vacio());

    @Autowired
    @Lazy
    private RecalculoPendienteSseService sseService;

    /**
     * Marca un cambio que afecta a TODOS los productos/canales (ej: cambio de un concepto
     * de cálculo, una clasificación gastro, un proveedor con muchos productos, etc.).
     * Cuando se aplique, se ejecutará el recálculo masivo completo.
     */
    public void marcarTodo(String motivo) {
        synchronized (this) {
            recalcularTodo.set(true);
            registrarComunInterno(motivo);
            actualizarSnapshot();
        }
        broadcast();
    }

    /**
     * Marca un cambio scoped a un producto específico (ej: editar margen, costo, IVA,
     * relación de un solo producto). Al aplicar, se recalcula SOLO ese producto.
     */
    public void marcarProducto(String motivo, Integer productoId) {
        synchronized (this) {
            if (productoId != null) productosPendientes.add(productoId);
            registrarComunInterno(motivo);
            actualizarSnapshot();
        }
        broadcast();
    }

    /**
     * Marca un cambio scoped a un canal específico (ej: cuotas del canal, regla de canal,
     * regla de excepción de un concepto en ese canal). Al aplicar, se recalcula SOLO ese canal.
     */
    public void marcarCanal(String motivo, Integer canalId) {
        synchronized (this) {
            if (canalId != null) canalesPendientes.add(canalId);
            registrarComunInterno(motivo);
            actualizarSnapshot();
        }
        broadcast();
    }

    /**
     * Marca un cambio que afecta a un conjunto acotado de productos (ej: cambio en MLA con
     * N SKUs asociados, cambio de financiación de un proveedor con N productos). Es equivalente
     * a llamar {@link #marcarProducto} N veces, pero registra el motivo una sola vez y emite
     * un único broadcast SSE — más eficiente y menos ruidoso en el banner.
     *
     * Si la lista está vacía no marca nada.
     */
    public void marcarProductos(String motivo, Iterable<Integer> productoIds) {
        synchronized (this) {
            int agregados = 0;
            for (Integer pid : productoIds) {
                if (pid != null) {
                    productosPendientes.add(pid);
                    agregados++;
                }
            }
            if (agregados == 0) return;
            registrarComunInterno(motivo);
            actualizarSnapshot();
        }
        broadcast();
    }

    /**
     * Marca un cambio que afecta a un conjunto acotado de canales (ej: cambio en concepto de
     * cálculo asignado a N canales). Mismo patrón que {@link #marcarProductos}: motivo único,
     * un solo broadcast.
     */
    public void marcarCanales(String motivo, Iterable<Integer> canalIds) {
        synchronized (this) {
            int agregados = 0;
            for (Integer cid : canalIds) {
                if (cid != null) {
                    canalesPendientes.add(cid);
                    agregados++;
                }
            }
            if (agregados == 0) return;
            registrarComunInterno(motivo);
            actualizarSnapshot();
        }
        broadcast();
    }

    /**
     * Compatibilidad con código legacy: si no se pasa scope, asumimos que afecta todo.
     * Los services nuevos deberían usar marcarTodo/marcarProducto/marcarCanal explícitos.
     */
    public void marcarPendiente(String motivo) {
        marcarTodo(motivo);
    }

    // Asume que el caller está dentro del synchronized(this).
    private void registrarComunInterno(String motivo) {
        cantidad.incrementAndGet();
        ultimaModificacion.set(LocalDateTime.now());
        contadorPorMotivo.computeIfAbsent(motivo, k -> new AtomicInteger(0)).incrementAndGet();
        fechaPorMotivo.computeIfAbsent(motivo, k -> new AtomicReference<>()).set(LocalDateTime.now());
        log.debug("Recálculo pendiente registrado: {} (total: {})", motivo, cantidad.get());
    }

    /**
     * Plan de recálculo a ejecutar al aplicar los pendientes.
     * Snapshot inmutable del estado actual (no se ve afectado por modificaciones posteriores).
     */
    public record PlanRecalculo(boolean recalcularTodo, Set<Integer> productos, Set<Integer> canales) {
        public boolean estaVacio() {
            return !recalcularTodo && productos.isEmpty() && canales.isEmpty();
        }
    }

    public synchronized PlanRecalculo plan() {
        return new PlanRecalculo(
                recalcularTodo.get(),
                Set.copyOf(productosPendientes),
                Set.copyOf(canalesPendientes)
        );
    }

    /**
     * Limpia el contador y los scopes. Sincronizado en el mismo monitor que las
     * operaciones marcar / plan para evitar race conditions: si un nuevo cambio
     * intenta entrar mientras se limpia, espera al lock y queda registrado para
     * el siguiente ciclo.
     */
    public void limpiar() {
        int previo;
        synchronized (this) {
            previo = cantidad.getAndSet(0);
            ultimaModificacion.set(null);
            contadorPorMotivo.clear();
            fechaPorMotivo.clear();
            recalcularTodo.set(false);
            productosPendientes.clear();
            canalesPendientes.clear();
            actualizarSnapshot();
        }
        if (previo > 0) {
            log.info("Recálculo pendiente limpiado (había {} cambios pendientes)", previo);
        }
        broadcast();
    }

    /**
     * Broadcast SSE FUERA del synchronized para no bloquear a otros threads que
     * intentan marcar pendientes mientras un cliente lento procesa el evento.
     * Mismo patrón que ProcesoGlobalService.adquirir/liberar.
     */
    private void broadcast() {
        if (sseService != null) {
            try {
                sseService.broadcast(snapshot.get());
            } catch (Exception e) {
                log.warn("Error al broadcast de recálculo pendiente por SSE: {}", e.getMessage());
            }
        }
    }

    /**
     * Devuelve el snapshot pre-construido. Lectura sin lock — siempre consistente
     * porque el DTO es inmutable y se reemplaza atómicamente desde dentro del synchronized.
     */
    public RecalculoPendienteDTO estado() {
        return snapshot.get();
    }

    // Asume que el caller está dentro del synchronized(this). Construye el DTO actual
    // y lo guarda en la AtomicReference para que estado()/broadcast() puedan leerlo
    // sin tomar el lock.
    //
    // Cantidad: cardinalidad del scope (productos+canales únicos). Si recalcularTodo=true,
    // se cuenta como 1 (representa "todo"). Esto evita que "3 edits al mismo producto"
    // se reporte como 3 cambios — son 3 calls pero 1 producto pendiente.
    private void actualizarSnapshot() {
        boolean todo = recalcularTodo.get();
        int productos = productosPendientes.size();
        int canales = canalesPendientes.size();
        int totalScope = (todo ? 1 : 0) + productos + canales;
        if (totalScope == 0) {
            snapshot.set(RecalculoPendienteDTO.vacio());
            return;
        }
        List<RecalculoPendienteDTO.MotivoPendiente> motivos = contadorPorMotivo.entrySet().stream()
                .map(e -> {
                    var fechaRef = fechaPorMotivo.get(e.getKey());
                    return new RecalculoPendienteDTO.MotivoPendiente(
                            e.getKey(),
                            e.getValue().get(),
                            fechaRef != null ? fechaRef.get() : null
                    );
                })
                .sorted((a, b) -> b.cantidad() - a.cantidad())
                .toList();
        snapshot.set(new RecalculoPendienteDTO(
                true, totalScope, todo, productos, canales,
                List.copyOf(productosPendientes),
                List.copyOf(canalesPendientes),
                ultimaModificacion.get(), motivos));
    }
}
