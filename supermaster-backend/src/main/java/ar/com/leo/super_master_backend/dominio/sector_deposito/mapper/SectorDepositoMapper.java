package ar.com.leo.super_master_backend.dominio.sector_deposito.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoCreateDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = GlobalMapperConfig.class)
public interface SectorDepositoMapper {

    SectorDepositoDTO toDTO(SectorDeposito entity);

    SectorDeposito toEntity(SectorDepositoCreateDTO dto);

    void updateEntityFromDTO(SectorDepositoUpdateDTO dto, @MappingTarget SectorDeposito entity);
}
