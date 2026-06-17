package ar.com.leo.super_master_backend.dominio.campania.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaDTO;
import ar.com.leo.super_master_backend.dominio.campania.dto.CampaniaProductoDTO;
import ar.com.leo.super_master_backend.dominio.campania.entity.Campania;
import ar.com.leo.super_master_backend.dominio.campania.entity.CampaniaProducto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface CampaniaMapper {

    @Mapping(target = "canalId", source = "canal.id")
    @Mapping(target = "canalNombre", source = "canal.nombre")
    @Mapping(target = "cantidadProductos", ignore = true)
    CampaniaDTO toDTO(Campania entity);

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "sku", source = "producto.sku")
    @Mapping(target = "descripcion", source = "producto.tituloDux")
    @Mapping(target = "costo", source = "producto.costo")
    CampaniaProductoDTO toProductoDTO(CampaniaProducto entity);
}
