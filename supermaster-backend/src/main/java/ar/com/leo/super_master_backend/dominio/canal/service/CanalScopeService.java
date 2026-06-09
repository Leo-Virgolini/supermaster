package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Resuelve el scope real de un cambio que afecta a un canal: el canal en sí + todos sus
 * subcanales (los que lo tienen como canalBase, recursivamente).
 *
 * <p>Es necesario porque los precios de un subcanal se calculan a partir del PVP del canal
 * padre. Cuando cambian las cuotas / conceptos / reglas / regla-descuento del padre, los
 * precios del subcanal también se desactualizan, aunque el subcanal mismo no haya cambiado.
 *
 * <p>El recálculo masivo ya maneja este orden con su "Pasada 1: canales base / Pasada 2:
 * dependientes", pero el flow scoped (banner Aplicar) solo recalcula los canales que se
 * marcan explícitamente. Sin esta resolución, los subcanales quedan con precios viejos.
 */
@Service
@RequiredArgsConstructor
public class CanalScopeService {

    private final CanalRepository canalRepository;

    /**
     * Devuelve el conjunto de IDs a recalcular cuando cambia un canal: {canalId} ∪
     * subcanales recursivos. El orden es: padre primero, después dependientes (preserva
     * la cadena de dependencia para el recálculo posterior).
     *
     * <p>Implementación BFS para soportar jerarquías arbitrarias (en la práctica son 1-2
     * niveles, pero el algoritmo no asume profundidad).
     */
    @Transactional(readOnly = true)
    public Set<Integer> idsConSubcanales(Integer canalId) {
        if (canalId == null) return Set.of();
        Set<Integer> resultado = new LinkedHashSet<>();
        Deque<Integer> pendientes = new ArrayDeque<>();
        pendientes.add(canalId);
        while (!pendientes.isEmpty()) {
            Integer actual = pendientes.poll();
            if (!resultado.add(actual)) continue;  // ya visitado (defensa contra ciclos)
            List<Canal> subcanales = canalRepository.findByCanalBaseId(actual);
            for (Canal sub : subcanales) {
                pendientes.add(sub.getId());
            }
        }
        return resultado;
    }
}
