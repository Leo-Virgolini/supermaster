package ar.com.leo.super_master_backend.dominio.regla_descuento.mapper;

import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.catalogo.entity.Catalogo;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoCreateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.dto.ReglaDescuentoUpdateDTO;
import ar.com.leo.super_master_backend.dominio.regla_descuento.entity.ReglaDescuento;
import org.mapstruct.*;

@Mapper(config = GlobalMapperConfig.class)
public interface ReglaDescuentoMapper {

    // =====================================================
    // ENTITY → DTO
    // =====================================================
    @Mapping(source = "canal.id", target = "canalId")
    @Mapping(source = "catalogo.id", target = "catalogoId")
    @Mapping(source = "clasifGral.id", target = "clasifGralId")
    @Mapping(source = "clasifGastro.id", target = "clasifGastroId")
    ReglaDescuentoDTO toDTO(ReglaDescuento entity);

    // =====================================================
    // CREATE DTO → ENTITY
    // =====================================================
    @Mapping(target = "canal", expression = "java(new Canal(dto.canalId()))")
    @Mapping(target = "catalogo", expression = "java(dto.catalogoId() != null ? new Catalogo(dto.catalogoId()) : null)")
    @Mapping(target = "clasifGral", expression = "java(dto.clasifGralId() != null ? new ClasifGral(dto.clasifGralId()) : null)")
    @Mapping(target = "clasifGastro", expression = "java(dto.clasifGastroId() != null ? new ClasifGastro(dto.clasifGastroId()) : null)")
    ReglaDescuento toEntity(ReglaDescuentoCreateDTO dto);

    // =====================================================
    // UPDATE DTO → ENTITY (PATCH)
    // =====================================================
    @Mapping(target = "canal", expression = "java(dto.canalId() != null ? new Canal(dto.canalId()) : entity.getCanal())")
    @Mapping(target = "catalogo", expression = "java(dto.catalogoId() != null ? new Catalogo(dto.catalogoId()) : entity.getCatalogo())")
    @Mapping(target = "clasifGral", expression = "java(dto.clasifGralId() != null ? new ClasifGral(dto.clasifGralId()) : entity.getClasifGral())")
    @Mapping(target = "clasifGastro", expression = "java(dto.clasifGastroId() != null ? new ClasifGastro(dto.clasifGastroId()) : entity.getClasifGastro())")
    void updateEntityFromDTO(ReglaDescuentoUpdateDTO dto, @MappingTarget ReglaDescuento entity);
}