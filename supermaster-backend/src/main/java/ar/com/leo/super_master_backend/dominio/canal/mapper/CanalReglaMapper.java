package ar.com.leo.super_master_backend.dominio.canal.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaCreateDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaDTO;
import ar.com.leo.super_master_backend.dominio.canal.dto.CanalReglaUpdateDTO;
import ar.com.leo.super_master_backend.dominio.canal.entity.Canal;
import ar.com.leo.super_master_backend.dominio.canal.entity.CanalRegla;
import ar.com.leo.super_master_backend.dominio.canal.entity.TipoRegla;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

@Mapper(config = GlobalMapperConfig.class)
public interface CanalReglaMapper {

    @Mapping(source = "canal.id", target = "canalId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "enumToString")
    @Mapping(source = "tipo.id", target = "tipoId")
    @Mapping(source = "marca.id", target = "marcaId")
    @Mapping(source = "clasifGral.id", target = "clasifGralId")
    @Mapping(source = "clasifGastro.id", target = "clasifGastroId")
    @Mapping(source = "producto.id", target = "productoId")
    CanalReglaDTO toDTO(CanalRegla entity);

    @Mapping(source = "canalId", target = "canal", qualifiedByName = "canalFromId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "stringToEnum")
    @Mapping(source = "tipoId", target = "tipo", qualifiedByName = "tipoFromId")
    @Mapping(source = "marcaId", target = "marca", qualifiedByName = "marcaFromId")
    @Mapping(source = "clasifGralId", target = "clasifGral", qualifiedByName = "clasifGralFromId")
    @Mapping(source = "clasifGastroId", target = "clasifGastro", qualifiedByName = "clasifGastroFromId")
    @Mapping(source = "productoId", target = "producto", qualifiedByName = "productoFromId")
    CanalRegla toEntity(CanalReglaCreateDTO dto);

    @Mapping(source = "canalId", target = "canal", qualifiedByName = "canalFromId")
    @Mapping(source = "tipoRegla", target = "tipoRegla", qualifiedByName = "stringToEnum")
    @Mapping(source = "tipoId", target = "tipo", qualifiedByName = "tipoFromId")
    @Mapping(source = "marcaId", target = "marca", qualifiedByName = "marcaFromId")
    @Mapping(source = "clasifGralId", target = "clasifGral", qualifiedByName = "clasifGralFromId")
    @Mapping(source = "clasifGastroId", target = "clasifGastro", qualifiedByName = "clasifGastroFromId")
    @Mapping(source = "productoId", target = "producto", qualifiedByName = "productoFromId")
    void updateEntityFromDTO(CanalReglaUpdateDTO dto, @MappingTarget CanalRegla entity);

    @Named("enumToString")
    default String enumToString(TipoRegla tipoRegla) {
        return tipoRegla != null ? tipoRegla.name() : null;
    }

    @Named("stringToEnum")
    default TipoRegla stringToEnum(String tipoRegla) {
        if (tipoRegla == null || tipoRegla.isBlank()) {
            return TipoRegla.EXCLUIR;
        }
        try {
            return TipoRegla.valueOf(tipoRegla.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TipoRegla.EXCLUIR;
        }
    }

    @Named("canalFromId")
    default Canal canalFromId(Integer id) {
        return id != null ? new Canal(id) : null;
    }

    @Named("tipoFromId")
    default Tipo tipoFromId(Integer id) {
        return id != null ? new Tipo(id) : null;
    }

    @Named("marcaFromId")
    default Marca marcaFromId(Integer id) {
        return id != null ? new Marca(id) : null;
    }

    @Named("clasifGralFromId")
    default ClasifGral clasifGralFromId(Integer id) {
        return id != null ? new ClasifGral(id) : null;
    }

    @Named("clasifGastroFromId")
    default ClasifGastro clasifGastroFromId(Integer id) {
        return id != null ? new ClasifGastro(id) : null;
    }

    @Named("productoFromId")
    default Producto productoFromId(Integer id) {
        return id != null ? new Producto(id) : null;
    }
}
