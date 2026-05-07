package ar.com.leo.super_master_backend.apis.ml.repository;

import ar.com.leo.super_master_backend.apis.ml.entity.ConfiguracionMl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionMlRepository extends JpaRepository<ConfiguracionMl, Integer> {

    Optional<ConfiguracionMl> findFirstByOrderByIdAsc();
}
