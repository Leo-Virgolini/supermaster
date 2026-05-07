package ar.com.leo.super_master_backend.dominio.cliente.mapper;

import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteCreateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteUpdateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface ClienteMapper {

    // =============================
    // ENTITY → DTO
    // =============================
    ClienteDTO toDTO(Cliente entity);

    // =============================
    // CREATE DTO → ENTITY
    // =============================
    Cliente toEntity(ClienteCreateDTO dto);

    // =============================
    // UPDATE DTO → ENTITY (PATCH)
    // =============================
    void updateEntityFromDTO(ClienteUpdateDTO dto, @MappingTarget Cliente entity);
}