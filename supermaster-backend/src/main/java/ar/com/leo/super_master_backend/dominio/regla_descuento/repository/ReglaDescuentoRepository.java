package ar.com.leo.super_master_backend.dominio.regla_descuento.repository;

import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReglaDescuentoRepository extends JpaRepository<ReglaDescuento, Integer> {

    List<ReglaDescuento> findByCanalId(Integer canalId);

    boolean existsByCatalogoId(Integer catalogoId);

    List<ReglaDescuento> findByCanalIdAndActivoTrueOrderByPrioridadAsc(Integer canalId);

    List<ReglaDescuento> findByCanalIdInAndActivoTrueOrderByCanalIdAscPrioridadAsc(List<Integer> canalIds);
}
