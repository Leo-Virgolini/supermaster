package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.*;

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
