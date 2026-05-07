package ar.com.leo.super_master_backend.dominio.auditoria.repository;

import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaAccion;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaCambio;
import ar.com.leo.super_master_backend.dominio.auditoria.entity.AuditoriaEntidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditoriaCambioRepository extends JpaRepository<AuditoriaCambio, Integer> {

    Page<AuditoriaCambio> findByEntidadAndEntidadId(AuditoriaEntidad entidad, Integer entidadId, Pageable pageable);

    @Query("""
            select ac
            from AuditoriaCambio ac
            where (:entidad is null or ac.entidad = :entidad)
              and (:entidadId is null or ac.entidadId = :entidadId)
              and (:accion is null or ac.accion = :accion)
              and (:campo is null or lower(ac.campo) = lower(:campo))
              and (:origen is null or lower(ac.origen) = lower(:origen))
              and (:fechaDesde is null or ac.fechaHora >= :fechaDesde)
              and (:fechaHasta is null or ac.fechaHora <= :fechaHasta)
              and (:usuario is null
                   or lower(coalesce(ac.usuarioNombreCompleto, '')) like lower(concat('%', :usuario, '%'))
                   or lower(coalesce(ac.usuarioUsername, '')) like lower(concat('%', :usuario, '%')))
              and (:search is null
                   or lower(coalesce(ac.entidadCodigo, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ac.campo, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ac.valorAnterior, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ac.valorNuevo, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ac.usuarioNombreCompleto, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ac.usuarioUsername, '')) like lower(concat('%', :search, '%')))
            """)
    Page<AuditoriaCambio> buscar(
            @Param("search") String search,
            @Param("entidad") AuditoriaEntidad entidad,
            @Param("accion") AuditoriaAccion accion,
            @Param("campo") String campo,
            @Param("origen") String origen,
            @Param("usuario") String usuario,
            @Param("entidadId") Integer entidadId,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable
    );
}
