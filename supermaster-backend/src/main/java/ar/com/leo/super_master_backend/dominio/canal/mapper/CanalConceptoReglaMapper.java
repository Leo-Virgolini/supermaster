package ar.com.leo.super_master_backend.dominio.canal.mapper;

import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalConceptoReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalConceptoRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.TipoRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = GlobalMapperConfig.class)
public interface CanalConceptoReglaMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "canal.id", target = "canalId")
    @Mapping(source = "concepto.id", target = "conceptoId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "enumToString")
    @Mapping(source = "tipo.id", target = "tipoId")
    @Mapping(source = "clasifGastro.id", target = "clasifGastroId")
    @Mapping(source = "clasifGral.id", target = "clasifGralId")
    @Mapping(source = "marca.id", target = "marcaId")
    CanalConceptoReglaDTO toDTO(CanalConceptoRegla entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(source = "canalId", target = "canal", qualifiedByName = "canalFromId")
    @Mapping(source = "conceptoId", target = "concepto", qualifiedByName = "conceptoFromId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "stringToEnum")
    @Mapping(source = "tipoId", target = "tipo", qualifiedByName = "tipoFromId")
    @Mapping(source = "clasifGastroId", target = "clasifGastro", qualifiedByName = "clasifGastroFromId")
    @Mapping(source = "clasifGralId", target = "clasifGral", qualifiedByName = "clasifGralFromId")
    @Mapping(source = "marcaId", target = "marca", qualifiedByName = "marcaFromId")
    CanalConceptoRegla toEntity(CanalConceptoReglaCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(source = "canalId", target = "canal", qualifiedByName = "canalFromId")
    @Mapping(source = "conceptoId", target = "concepto", qualifiedByName = "conceptoFromId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "stringToEnum")
    @Mapping(source = "tipoId", target = "tipo", qualifiedByName = "tipoFromId")
    @Mapping(source = "clasifGastroId", target = "clasifGastro", qualifiedByName = "clasifGastroFromId")
    @Mapping(source = "clasifGralId", target = "clasifGral", qualifiedByName = "clasifGralFromId")
    @Mapping(source = "marcaId", target = "marca", qualifiedByName = "marcaFromId")
    void updateEntityFromDTO(CanalConceptoReglaUpdateDTO dto, @MappingTarget CanalConceptoRegla entity);

    // =============================
    // MÉTODOS DE CONVERSIÓN
    // =============================
    @Named("enumToString")
    default String enumToString(TipoRegla tipoRegla) {
        return tipoRegla != null ? tipoRegla.name() : null;
    }

    @Named("stringToEnum")
    default TipoRegla stringToEnum(String tipoRegla) {
        if (tipoRegla == null || tipoRegla.isBlank()) {
            return TipoRegla.EXCLUIR; // Valor por defecto
        }
        try {
            return TipoRegla.valueOf(tipoRegla.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TipoRegla.EXCLUIR; // Valor por defecto si no es válido
        }
    }

    @Named("canalFromId")
    default Canal canalFromId(Integer id) {
        return id != null ? new Canal(id) : null;
    }

    @Named("conceptoFromId")
    default ConceptoCalculo conceptoFromId(Integer id) {
        return id != null ? new ConceptoCalculo(id) : null;
    }

    @Named("tipoFromId")
    default Tipo tipoFromId(Integer id) {
        return id != null ? new Tipo(id) : null;
    }

    @Named("clasifGastroFromId")
    default ClasifGastro clasifGastroFromId(Integer id) {
        return id != null ? new ClasifGastro(id) : null;
    }

    @Named("clasifGralFromId")
    default ClasifGral clasifGralFromId(Integer id) {
        return id != null ? new ClasifGral(id) : null;
    }

    @Named("marcaFromId")
    default Marca marcaFromId(Integer id) {
        return id != null ? new Marca(id) : null;
    }
}

