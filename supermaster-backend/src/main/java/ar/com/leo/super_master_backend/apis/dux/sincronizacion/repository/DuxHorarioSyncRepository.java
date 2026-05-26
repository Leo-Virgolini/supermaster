package ar.com.leo.super_master_backend.apis.dux.sincronizacion.repository;

import ar.com.leo.super_master_backend.apis.dux.sincronizacion.entity.DuxHorarioSync;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DuxHorarioSyncRepository extends JpaRepository<DuxHorarioSync, Long> {

    List<DuxHorarioSync> findAllByOrderByHoraAscMinutoAsc();
}
