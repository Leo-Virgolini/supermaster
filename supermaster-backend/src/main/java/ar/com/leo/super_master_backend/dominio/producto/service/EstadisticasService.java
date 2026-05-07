package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.MargenesPorCuotasDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.ProductoMargenNegativo;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.ProductosPorCatalogo;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.ProductosPorProveedor;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.ResumenDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.CuotaDisponibleDTO;

import java.util.List;

public interface EstadisticasService {
    EstadisticasDTO obtenerEstadisticas();
    ResumenDTO obtenerResumen();
    List<CuotaDisponibleDTO> obtenerCuotasDisponibles();
    MargenesPorCuotasDTO obtenerMargenesPorCuotas(Integer cuotas);
    List<ProductosPorProveedor> obtenerProductosPorProveedor();
    List<ProductosPorCatalogo> obtenerProductosPorCatalogo();
    List<ProductoMargenNegativo> obtenerProductosConMargenNegativo();
}
