package ar.com.leo.super_master_backend.dominio.reposicion.service;

import ar.com.leo.super_master_backend.dominio.common.dto.ProcesoMasivoEstadoDTO;
import ar.com.leo.super_master_backend.dominio.orden_compra.dto.OrdenCompraDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.AjustePedidoDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionConfigDTO;
import ar.com.leo.super_master_backend.dominio.reposicion.dto.ReposicionResultDTO;

import ar.com.leo.super_master_backend.apis.dux.model.FacturaDux;
import ar.com.leo.super_master_backend.apis.dux.service.DuxService.DuxItemData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ReposicionService {

    boolean iniciarCalculo();

    boolean cancelarCalculo();

    ProcesoMasivoEstadoDTO obtenerEstadoCalculo();

    ReposicionResultDTO obtenerResultado();

    ReposicionConfigDTO obtenerConfig();

    ReposicionConfigDTO actualizarConfig(ReposicionConfigDTO dto);

    ExcelResult generarExcelSugerencias();

    ExcelResult generarExcelOrdenCompra(Integer ordenCompraId);

    record ExcelResult(byte[] data, String filename) {}

    void calcularAsync();

    ReposicionResultDTO ajustarPedidos(AjustePedidoDTO dto);

    List<OrdenCompraDTO> generarOrdenesDesdeResultado(Integer proveedorId);

    List<Integer> persistirDatosDux(Map<String, DuxItemData> dataMap);

    void persistirVentasEnCache(List<FacturaDux> facturas, LocalDate fechaDesde, LocalDate fechaHasta);

    void invalidarCacheVentas();

    void limpiarCacheVentas(LocalDate cutoff);
}
