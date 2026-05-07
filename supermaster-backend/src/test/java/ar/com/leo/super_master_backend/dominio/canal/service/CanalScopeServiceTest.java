package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.repository.CanalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests del resolver de subcanales. Cubre:
 *  - Canal sin subcanales → solo el canal mismo.
 *  - Subcanales directos.
 *  - Cadena recursiva (subcanal de subcanal).
 *  - Defensa contra ciclos.
 *  - canalId null → empty.
 *  - Orden: padre antes que dependientes.
 */
@ExtendWith(MockitoExtension.class)
class CanalScopeServiceTest {

    @Mock
    private CanalRepository canalRepository;

    @InjectMocks
    private CanalScopeService service;

    private Canal canal(int id) {
        return new Canal(id);
    }

    @Test
    @DisplayName("canalId null → set vacío")
    void canalIdNull_vacio() {
        assertThat(service.idsConSubcanales(null)).isEmpty();
    }

    @Test
    @DisplayName("canal sin subcanales → solo el canal mismo")
    void sinSubcanales() {
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of());

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactly(1);
    }

    @Test
    @DisplayName("canal con N subcanales directos → {canal, ...subcanales}")
    void subcanalesDirectos() {
        // Canal 1 tiene a 10, 20, 30 como subcanales directos. Ninguno tiene sub-subcanales.
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(10), canal(20), canal(30)));
        when(canalRepository.findByCanalBaseId(10)).thenReturn(List.of());
        when(canalRepository.findByCanalBaseId(20)).thenReturn(List.of());
        when(canalRepository.findByCanalBaseId(30)).thenReturn(List.of());

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactlyInAnyOrder(1, 10, 20, 30);
    }

    @Test
    @DisplayName("subcanales recursivos: B subcanal de A, C subcanal de B → todos")
    void subcanalesRecursivos() {
        // Cadena: 1 → 2 → 3 (3 es subcanal de 2, 2 es subcanal de 1).
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(2)));
        when(canalRepository.findByCanalBaseId(2)).thenReturn(List.of(canal(3)));
        when(canalRepository.findByCanalBaseId(3)).thenReturn(List.of());

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactly(1, 2, 3);  // BFS preserva orden topológico.
    }

    @Test
    @DisplayName("árbol con varios niveles: padre → 2 subcanales, cada uno con sus subcanales")
    void arbolMultinivel() {
        // 1 → {2, 3}; 2 → {4}; 3 → {5, 6}.
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(2), canal(3)));
        when(canalRepository.findByCanalBaseId(2)).thenReturn(List.of(canal(4)));
        when(canalRepository.findByCanalBaseId(3)).thenReturn(List.of(canal(5), canal(6)));
        lenient().when(canalRepository.findByCanalBaseId(4)).thenReturn(List.of());
        lenient().when(canalRepository.findByCanalBaseId(5)).thenReturn(List.of());
        lenient().when(canalRepository.findByCanalBaseId(6)).thenReturn(List.of());

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("ciclo: A subcanal de B y B subcanal de A → no infinite loop, todos visitados una vez")
    void cicloProtegido() {
        // 1 ↔ 2 (ciclo directo). El BFS visita cada uno solo una vez.
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(2)));
        when(canalRepository.findByCanalBaseId(2)).thenReturn(List.of(canal(1)));

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("ciclo indirecto: 1 → 2 → 3 → 1 → no loop infinito")
    void cicloIndirectoProtegido() {
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(2)));
        when(canalRepository.findByCanalBaseId(2)).thenReturn(List.of(canal(3)));
        when(canalRepository.findByCanalBaseId(3)).thenReturn(List.of(canal(1)));

        Set<Integer> result = service.idsConSubcanales(1);

        assertThat(result).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    @DisplayName("orden BFS: el canal padre aparece primero")
    void ordenPadrePrimero() {
        when(canalRepository.findByCanalBaseId(1)).thenReturn(List.of(canal(2)));
        when(canalRepository.findByCanalBaseId(2)).thenReturn(List.of(canal(3)));
        when(canalRepository.findByCanalBaseId(3)).thenReturn(List.of());

        Set<Integer> result = service.idsConSubcanales(1);

        // El primer elemento debe ser el padre — el set es LinkedHashSet (orden de inserción).
        assertThat(result.iterator().next()).isEqualTo(1);
    }
}
