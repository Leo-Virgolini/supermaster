package ar.com.leo.super_master_backend.dominio.canal.service;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaPatchDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CanalReglaService {

    Page<CanalReglaDTO> listar(String search, Pageable pageable);

    CanalReglaDTO obtener(Long id);

    CanalReglaDTO crear(CanalReglaCreateDTO dto);

    CanalReglaDTO actualizar(Long id, CanalReglaUpdateDTO dto);

    CanalReglaDTO patch(Long id, CanalReglaPatchDTO patch);

    void eliminar(Long id);

    List<CanalReglaDTO> listarPorCanal(Integer canalId);

    /**
     * Determina si un producto aplica al canal (no está excluido).
     * Evalúa las reglas del canal con semántica:
     *   - Si hay reglas INCLUIR: el producto debe cumplir al menos una.
     *   - Cualquier regla EXCLUIR que cumpla deja al producto fuera.
     */
    boolean productoAplicaAlCanal(Integer canalId, Producto producto);
}
