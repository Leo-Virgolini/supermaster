package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.cliente.entity.Cliente;
import ar.com.leo.super_master_backend.dominio.producto.dto.ProductoClienteDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCliente;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoClienteId;
import ar.com.leo.super_master_backend.dominio.producto.mapper.ProductoClienteMapper;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoClienteRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.cliente.repository.ClienteRepository;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductoClienteServiceImpl implements ProductoClienteService {

    private final ProductoClienteRepository repo;
    private final ProductoClienteMapper mapper;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductoClienteDTO> listar(Integer productoId) {
        return repo.findByProductoId(productoId)
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public ProductoClienteDTO agregar(Integer productoId, Integer clienteId) {
        // Validar que existan
        productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));

        // Verificar si ya existe
        ProductoClienteId id = new ProductoClienteId(productoId, clienteId);
        if (repo.findById(id).isPresent()) {
            throw new ConflictException("La relación Producto-Cliente ya existe");
        }

        ProductoCliente entity = new ProductoCliente();

        entity.setId(id);
        entity.setProducto(new Producto(productoId));
        entity.setCliente(new Cliente(clienteId));

        repo.save(entity);

        return mapper.toDTO(entity);
    }

    @Override
    @Transactional
    public void eliminar(Integer productoId, Integer clienteId) {
        ProductoClienteId id = new ProductoClienteId(productoId, clienteId);
        if (repo.findById(id).isEmpty()) {
            throw new NotFoundException("Relación Producto-Cliente no existe");
        }
        repo.deleteByProductoIdAndClienteId(productoId, clienteId);
    }

}
