package ar.com.leo.super_master_backend.dominio.marca.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaCreateDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaDTO;
import ar.com.leo.super_master_backend.dominio.marca.dto.MarcaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface MarcaMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    @Mapping(source = "padre.id", target = "padreId")
    @Mapping(target = "nombreCompleto", expression = "java(buildNombreCompleto(entity))")
    MarcaDTO toDTO(Marca entity);

    /** Construye "ABUELO > PADRE > HIJO" navegando recursivamente la cadena de padres. */
    default String buildNombreCompleto(Marca m) {
        return buildNombreCompleto(m, new java.util.HashSet<>());
    }

    /** Corta ciclos (datos con padre = sí mismo u otro ancestro) para no caer en StackOverflow. */
    private String buildNombreCompleto(Marca m, java.util.Set<Integer> visitados) {
        if (m == null) return null;
        if (m.getId() != null && !visitados.add(m.getId())) return m.getNombre(); // ciclo: corta
        if (m.getPadre() == null) return m.getNombre();
        return buildNombreCompleto(m.getPadre(), visitados) + " > " + m.getNombre();
    }

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Marca(dto.padreId()) : null)")
    Marca toEntity(MarcaCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    @Mapping(target = "padre", expression = "java(dto.padreId() != null ? new Marca(dto.padreId()) : entity.getPadre())")
    void updateEntityFromDTO(MarcaUpdateDTO dto, @MappingTarget Marca entity);
}