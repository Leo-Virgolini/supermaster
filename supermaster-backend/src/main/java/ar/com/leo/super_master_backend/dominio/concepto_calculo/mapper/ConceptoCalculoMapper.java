package ar.com.leo.super_master_backend.dominio.concepto_calculo.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoCreateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.dto.ConceptoCalculoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.AplicaSobre;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.ConceptoCalculo;
import ar.com.leo.super_master_backend.dominio.concepto_calculo.entity.NaturalezaConcepto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = GlobalMapperConfig.class)
public interface ConceptoCalculoMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    // El campo `naturaleza` del DTO siempre devuelve la naturaleza EFECTIVA
    // (override de la columna o default del aplicaSobre), nunca null.
    @Mapping(source = "aplicaSobre", target = "aplicaSobre", qualifiedByName = "enumToString")
    @Mapping(source = "aplicaSobre", target = "etapa", qualifiedByName = "etapaFromAplicaSobre")
    @Mapping(target = "naturaleza", expression = "java(entity.getNaturalezaResolved() != null ? entity.getNaturalezaResolved().name() : null)")
    ConceptoCalculoDTO toDTO(ConceptoCalculo entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(source = "aplicaSobre", target = "aplicaSobre", qualifiedByName = "stringToEnum")
    @Mapping(source = "naturaleza", target = "naturaleza", qualifiedByName = "stringToNaturaleza")
    ConceptoCalculo toEntity(ConceptoCalculoCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(source = "aplicaSobre", target = "aplicaSobre", qualifiedByName = "stringToEnum")
    @Mapping(source = "naturaleza", target = "naturaleza", qualifiedByName = "stringToNaturaleza")
    void updateEntityFromDTO(ConceptoCalculoUpdateDTO dto, @MappingTarget ConceptoCalculo entity);

    // =============================
    // MÉTODOS DE CONVERSIÓN
    // =============================
    @Named("enumToString")
    default String enumToString(AplicaSobre aplicaSobre) {
        return aplicaSobre != null ? aplicaSobre.name() : null;
    }

    @Named("stringToEnum")
    default AplicaSobre stringToEnum(String aplicaSobre) {
        if (aplicaSobre == null || aplicaSobre.isBlank()) {
            return null; // Será ignorado por GlobalMapperConfig en updates, o fallará validación en creates
        }
        return AplicaSobre.valueOf(aplicaSobre.toUpperCase()); // Lanza IllegalArgumentException si es inválido
    }

    @Named("etapaFromAplicaSobre")
    default String etapaFromAplicaSobre(AplicaSobre aplicaSobre) {
        return aplicaSobre != null ? aplicaSobre.getEtapa().name() : null;
    }

    @Named("stringToNaturaleza")
    default NaturalezaConcepto stringToNaturaleza(String naturaleza) {
        if (naturaleza == null || naturaleza.isBlank()) {
            return null; // Permitido: la naturaleza es opcional (override).
        }
        return NaturalezaConcepto.valueOf(naturaleza.toUpperCase());
    }
}
