package ar.com.leo.super_master_backend.dominio.producto.mla.repository;

import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MlaRepository extends JpaRepository<Mla, Integer> {

    List<Mla> findByMla(String mla);

    Optional<Mla> findFirstByMla(String mla);

    Page<Mla> findByMlaContainingIgnoreCaseOrMlauContainingIgnoreCase(String mla, String mlau, Pageable pageable);

    List<Mla> findByMlaIsNotNullAndPrecioEnvioIsNull();

    List<Mla> findByTopePromocionGreaterThan(int valor);

    /** MLAs de una familia de variantes (para reflejar localmente el rename del family_name). */
    List<Mla> findByFamilyId(String familyId);
}
