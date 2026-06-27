package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.ImagenUso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface ImagenUsoRepository extends JpaRepository<ImagenUso, Long> {
    @Modifying
    @Query("UPDATE ImagenUso s SET s.consultas = s.consultas + 1, " +
           "s.tokensEntrada = s.tokensEntrada + :in, " +
           "s.tokensSalida = s.tokensSalida + :out, " +
           "s.costoUsd = s.costoUsd + :costo WHERE s.id = 1")
    int registrar(@Param("in") long tokensEntrada, @Param("out") long tokensSalida, @Param("costo") BigDecimal costo);
}
