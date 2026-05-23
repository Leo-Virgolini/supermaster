package ar.com.leo.super_master_backend.dominio.common.service;

import ar.com.leo.super_master_backend.dominio.common.dto.RecalculoPendienteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Broadcast del estado de recálculos pendientes vía Server-Sent Events.
 * Cada cliente conectado recibe un snapshot completo cuando se marca un nuevo
 * pendiente o se limpia el contador, y heartbeats periódicos para mantener viva
 * la conexión a través de proxies.
 */
@Slf4j
@Service
public class RecalculoPendienteSseService {

    private static final long EMITTER_TIMEOUT_MS = Long.MAX_VALUE;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(RecalculoPendienteDTO snapshotInicial) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("pendiente").data(snapshotInicial));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void broadcast(RecalculoPendienteDTO snapshot) {
        List<SseEmitter> snapshotEmitters = List.copyOf(emitters);
        for (SseEmitter emitter : snapshotEmitters) {
            try {
                emitter.send(SseEmitter.event().name("pendiente").data(snapshot));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception completeEx) { log.trace("emitter.complete() falló (ya cerrado)", completeEx); }
            }
        }
    }

    /**
     * Heartbeat cada 25s para que proxies y balanceadores no cierren la conexión ociosa.
     */
    @Scheduled(fixedDelay = 25_000)
    public void heartbeat() {
        List<SseEmitter> snapshotEmitters = List.copyOf(emitters);
        for (SseEmitter emitter : snapshotEmitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception completeEx) { log.trace("emitter.complete() falló (ya cerrado)", completeEx); }
            }
        }
    }
}
