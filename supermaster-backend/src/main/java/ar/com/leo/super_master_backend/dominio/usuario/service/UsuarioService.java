package ar.com.leo.super_master_backend.dominio.usuario.service;

import ar.com.leo.super_master_backend.dominio.usuario.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UsuarioService {

    Page<UsuarioDTO> listar(String search, Pageable pageable);

    UsuarioDTO obtener(Integer id);

    UsuarioDTO crear(UsuarioCreateDTO dto);

    UsuarioDTO actualizar(Integer id, UsuarioUpdateDTO dto);

    void cambiarPassword(Integer id, CambioPasswordDTO dto);

    void eliminar(Integer id);

    List<RolDTO> listarRoles();
}
