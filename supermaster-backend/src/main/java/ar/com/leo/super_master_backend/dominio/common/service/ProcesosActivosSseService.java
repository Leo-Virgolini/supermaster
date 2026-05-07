package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoActivoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcast de cambios en la lista de procesos activos vía Server-Sent Events.
 * Cada cliente conectado recibe un snapshot completo cuando algún proceso se adquiere
 * o libera, y heartbeats periódicos para mantener viva la conexión a través de proxies.
 *
 * <p>Además, mientras hay al menos un proceso activo, reemitimos el snapshot cada 2s
 * para que los clientes vean el progreso (procesados / total / errores) avanzar en el
 * badge sin tener que pollear. En idle (sin procesos), no se reemite nada — solo el
 * heartbeat cada 25s para mantener la conexión TCP viva.
 */
@Slf4j
@Service
public class ProcesosActivosSseService {

    // Sin timeout: mantenemos la conexión abierta indefinidamente. El cliente reconecta
    // si el server cierra (reinicio, kill, etc.) y el heartbeat detecta conexiones muertas.
    private static final long EMITTER_TIMEOUT_MS = Long.MAX_VALUE;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // Lazy para evitar ciclo: ProcesoGlobalService inyecta este servicio (lazy también),
    // y ahora este servicio llama de vuelta a ProcesoGlobalService.snapshotDTO() para
    // reemitir progreso periódicamente.
    @Autowired
    @Lazy
    private ProcesoGlobalService procesoGlobalService;

    public SseEmitter subscribe(ProcesoActivoDTO snapshotInicial) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("procesos").data(snapshotInicial));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcast(ProcesoActivoDTO snapshot) {
        List<SseEmitter> snapshotEmitters = List.copyOf(emitters);
        for (SseEmitter emitter : snapshotEmitters) {
            try {
                emitter.send(SseEmitter.event().name("procesos").data(snapshot));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Mientras hay al menos un proceso activo, reemite el snapshot cada 2s para
     * empujar el progreso (procesados / total / errores) a los clientes en (casi)
     * tiempo real. Si no hay procesos activos, no hace nada (el snapshot vacío ya
     * fue emitido cuando se liberó el último).
     */
    @Scheduled(fixedDelay = 2_000)
    public void emitirProgresoSiHayActivos() {
        if (procesoGlobalService == null) return;
        if (!procesoGlobalService.hayProcesoActivo()) return;
        if (emitters.isEmpty()) return;
        try {
            broadcast(procesoGlobalService.snapshotDTO());
        } catch (Exception e) {
            log.warn("Error en re-emit periódico del progreso: {}", e.getMessage());
        }
    }

    /**
     * Heartbeat cada 25s para que proxies y balanceadores no cierren la conexión ociosa.
     * SSE comment (línea `:`) no dispara onmessage en el cliente.
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        List<SseEmitter> snapshotEmitters = List.copyOf(emitters);
        for (SseEmitter emitter : snapshotEmitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
    }
}
