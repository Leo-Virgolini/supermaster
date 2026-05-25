package ar.com.leo.super_master_backend.dominio.cliente.service;

import ar.com.leo.super_master_backend.dominio.auditoria.dto.AuditoriaCambioDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteCreateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClienteUpdateDTO;
import ar.com.leo.super_master_backend.dominio.cliente.dto.ClientePatchDTO;
import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import ar.com.leo.super_master_backend.dominio.cliente.mapper.ClienteMapper;
import ar.com.leo.super_master_backend.dominio.cliente.repository.ClienteRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import static ar.com.leo.super_master_backend.dominio.common.util.JsonNullableFields.*;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository repo;
    private final ClienteMapper mapper;
    private final ProductoClienteRepository productoClienteRepository;
    private final ProductoMapper productoMapper;
    private final ClienteAuditoriaService clienteAuditoriaService;

    @Override
    @Transactional(readOnly = true)
    public Page<ClienteDTO> listar(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return repo.findByNombreContainingIgnoreCase(search, pageable)
                    .map(mapper::toDTO);
        }
        return repo.findAll(pageable)
                .map(mapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteDTO obtener(Integer id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
    }

    @Override
    @Transactional
    public ClienteDTO crear(ClienteCreateDTO dto) {
        Cliente entity = mapper.toEntity(dto);
        repo.save(entity);
        clienteAuditoriaService.registrarCreacion(entity);
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClienteDTO actualizar(Integer id, ClienteUpdateDTO dto) {
        Cliente entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        var estadoAnterior = clienteAuditoriaService.capturarSnapshot(entity);

        mapper.updateEntityFromDTO(dto, entity);

        repo.save(entity);
        clienteAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public ClienteDTO patch(Integer id, ClientePatchDTO patchDto) {
        if (!presente(patchDto.getNombre())) {
            throw new BadRequestException("El body del PATCH no puede estar vacío");
        }


        Cliente entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        var estadoAnterior = clienteAuditoriaService.capturarSnapshot(entity);

        entity.setNombre(leerStringRequerido(patchDto.getNombre(), "nombre", 45));

        repo.save(entity);
        clienteAuditoriaService.registrarActualizacion(id, estadoAnterior, entity);
        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Cliente entity = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        var estadoAnterior = clienteAuditoriaService.capturarSnapshot(entity);

        repo.delete(entity);
        clienteAuditoriaService.registrarEliminacion(id, estadoAnterior);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoResumenDTO> listarProductos(Integer clienteId) {
        if (!repo.existsById(clienteId)) {
            throw new NotFoundException("Cliente no encontrado");
        }
        return productoClienteRepository.findByClienteId(clienteId)
                .stream()
                .map(pc -> productoMapper.toResumenDTO(pc.getProducto()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditoriaCambioDTO> listarAuditoria(Integer clienteId, Pageable pageable) {
        if (!repo.existsById(clienteId)) {
            throw new NotFoundException("Cliente no encontrado");
        }
        return clienteAuditoriaService.listarPorCliente(clienteId, pageable);
    }


}






