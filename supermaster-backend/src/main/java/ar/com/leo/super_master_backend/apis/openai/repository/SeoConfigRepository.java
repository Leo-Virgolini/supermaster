package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.entity.SeoConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeoConfigRepository extends JpaRepository<SeoConfig, Long> {
}
