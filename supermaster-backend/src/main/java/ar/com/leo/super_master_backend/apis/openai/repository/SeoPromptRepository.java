package ar.com.leo.super_master_backend.apis.openai.repository;

import ar.com.leo.super_master_backend.apis.openai.SeoCanal;
import ar.com.leo.super_master_backend.apis.openai.entity.SeoPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SeoPromptRepository extends JpaRepository<SeoPrompt, Long> {
    Optional<SeoPrompt> findByCanal(SeoCanal canal);
}
