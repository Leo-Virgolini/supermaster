package ar.com.leo.super_master_backend.dominio.proveedor.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorCreateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.dto.ProveedorUpdateDTO;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ProveedorMapper {

    // ===============================
    // ENTITY → DTO
    // ===============================
    ProveedorDTO toDTO(Proveedor entity);

    // ===============================
    // CREATE DTO → ENTITY
    // ===============================
    Proveedor toEntity(ProveedorCreateDTO dto);

    // ===============================
    // UPDATE DTO → ENTITY (PATCH)
    // ===============================
    void updateEntityFromDTO(ProveedorUpdateDTO dto, @MappingTarget Proveedor entity);
}