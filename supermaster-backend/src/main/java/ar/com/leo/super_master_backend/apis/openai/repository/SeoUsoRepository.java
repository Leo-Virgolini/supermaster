package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.SeoUso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface SeoUsoRepository extends JpaRepository<SeoUso, Long> {

    /** Incremento atómico de la fila singleton (id=1): evita lost-updates con generaciones en paralelo. */
    @Modifying
    @Query("UPDATE SeoUso s SET s.consultas = s.consultas + 1, " +
           "s.tokensEntrada = s.tokensEntrada + :in, " +
           "s.tokensSalida = s.tokensSalida + :out, " +
           "s.costoUsd = s.costoUsd + :costo WHERE s.id = 1")
    int registrar(@Param("in") long tokensEntrada, @Param("out") long tokensSalida, @Param("costo") BigDecimal costo);

    /** Reset de todos los contadores de uso (singleton id=1). */
    @Modifying
    @Query("UPDATE SeoUso s SET s.consultas = 0, s.tokensEntrada = 0, " +
           "s.tokensSalida = 0, s.costoUsd = 0 WHERE s.id = 1")
    int reset();
}
