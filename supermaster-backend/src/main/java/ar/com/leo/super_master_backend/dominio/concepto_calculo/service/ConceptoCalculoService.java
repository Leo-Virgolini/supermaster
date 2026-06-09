package ar.com.leo.super_master_backend.dominio.concepto_calculo.service;

import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoCreateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoPatchDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface ConceptoCalculoService {

    Page<ConceptoCalculoDTO> listar(String search, Pageable pageable);


    ConceptoCalculoDTO obtener(Integer id);

    ConceptoCalculoDTO crear(ConceptoCalculoCreateDTO dto);

    ConceptoCalculoDTO actualizar(Integer id, ConceptoCalculoUpdateDTO dto);

    ConceptoCalculoDTO patch(Integer id, ConceptoCalculoPatchDTO patch);

    void eliminar(Integer id);
}

