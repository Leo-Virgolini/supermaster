package ar.com.leo.super_master_backend.dominio.config_automatizacion.service;

import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionCreateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionUpdateDTO;
import ar.com.leo.super_master_backend.dominio.config_automatizacion.dto.ConfigAutomatizacionPatchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ConfigAutomatizacionService {

    Page<ConfigAutomatizacionDTO> listar(String search, Pageable pageable);

    ConfigAutomatizacionDTO obtener(Integer id);

    Optional<ConfigAutomatizacionDTO> obtenerPorClave(String clave);

    ConfigAutomatizacionDTO crear(ConfigAutomatizacionCreateDTO dto);

    ConfigAutomatizacionDTO actualizar(Integer id, ConfigAutomatizacionUpdateDTO dto);

    ConfigAutomatizacionDTO patch(Integer id, ConfigAutomatizacionPatchDTO patch);

    void eliminar(Integer id);
}

