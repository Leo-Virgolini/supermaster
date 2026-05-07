package ar.com.leo.super_master_backend.dominio.reposicion.repository;

import ar.com.leo.super_master_backend.dominio.reposicion.entity.VentaDiariaCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface VentaDiariaCacheRepository extends JpaRepository<VentaDiariaCache, Integer> {

    @Query("SELECT v.sku, SUM(v.cantidad) FROM VentaDiariaCache v " +
            "WHERE v.fecha >= :desde AND v.fecha <= :hasta " +
            "GROUP BY v.sku")
    List<Object[]> sumarVentasPorSkuEnRango(LocalDate desde, LocalDate hasta);

    @Modifying
    @Query("DELETE FROM VentaDiariaCache v WHERE v.fecha < :cutoff")
    void eliminarAnterioresA(LocalDate cutoff);

    @Modifying
    @Query("DELETE FROM VentaDiariaCache v WHERE v.fecha >= :desde AND v.fecha <= :hasta")
    void eliminarEnRango(LocalDate desde, LocalDate hasta);

    @Modifying
    @Query("DELETE FROM VentaDiariaCache v")
    void eliminarTodo();
}
