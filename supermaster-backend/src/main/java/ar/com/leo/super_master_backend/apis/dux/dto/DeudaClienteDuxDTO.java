package ar.com.leo.super_master_backend.apis.dux.dto;

import ar.com.leo.super_master_backend.apis.dux.model.FacturaDux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DeudaClienteDuxDTO(
        Long id,
        Long idCliente,
        String cliente,
        String cuit,
        String tipoComp,
        String letraComp,
        Integer nroComp,
        String nroPtoVta,
        String fecha,
        BigDecimal total,
        BigDecimal montoGravado,
        BigDecimal montoIva,
        BigDecimal montoExento,
        BigDecimal montoDesc,
        Boolean anulada,
        String urlFactura,
        String vendedor,
        BigDecimal cobrado,
        BigDecimal saldo,
        List<DetalleDTO> detalles,
        List<CobroDTO> cobros,
        List<FacturaRefDTO> facturasReferenciadas
) {

    public record FacturaRefDTO(
            String numeroPuntoDeVenta,
            Integer numeroComprobante,
            String letraComprobante,
            String tipoComprobante
    ) {}

    public record DetalleDTO(
            String codItem,
            String item,
            BigDecimal cantidad,
            BigDecimal precioUni,
            BigDecimal porcDesc,
            BigDecimal porcIva
    ) {}

    public record CobroDTO(
            String numeroPuntoDeVenta,
            Integer numeroComprobante,
            String personal,
            String caja,
            List<MovCobroDTO> movimientos
    ) {}

    public record MovCobroDTO(
            String tipoDeValor,
            String referencia,
            BigDecimal monto
    ) {}

    public static DeudaClienteDuxDTO fromFacturaDux(FacturaDux f, Map<Long, String> personalesMap) {
        String clienteNombre = f.getApellidoRazonSoc();
        if (f.getNombre() != null && !f.getNombre().isBlank()) {
            clienteNombre = clienteNombre != null ? clienteNombre + " " + f.getNombre() : f.getNombre();
        }

        List<DetalleDTO> detalles = f.getDetalles() != null
                ? f.getDetalles().stream().map(d -> new DetalleDTO(
                        d.getCodItem(),
                        d.getItem(),
                        parseBigDecimal(d.getCtd()),
                        parseBigDecimal(d.getPrecioUni()),
                        parseBigDecimal(d.getPorcDesc()),
                        parseBigDecimal(d.getPorcIva())
                )).toList()
                : List.of();

        List<CobroDTO> cobros = f.getDetallesCobro() != null
                ? f.getDetallesCobro().stream().map(c -> new CobroDTO(
                        c.getNumeroPuntoDeVenta(),
                        c.getNumeroComprobante(),
                        c.getPersonal(),
                        c.getCaja(),
                        c.getDetallesMovCobro() != null
                                ? c.getDetallesMovCobro().stream().map(m -> new MovCobroDTO(
                                        m.getTipoDeValor(),
                                        m.getReferencia(),
                                        m.getMonto() != null ? BigDecimal.valueOf(m.getMonto()) : BigDecimal.ZERO
                                )).toList()
                                : List.of()
                )).toList()
                : List.of();

        String vendedorNombre = null;
        if (f.getIdVendedor() != null && personalesMap != null) {
            vendedorNombre = personalesMap.get(f.getIdVendedor());
        }

        BigDecimal totalFactura = parseBigDecimal(f.getTotal());
        BigDecimal totalCobrado = cobros.stream()
                .flatMap(c -> c.movimientos().stream())
                .map(MovCobroDTO::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldo = totalFactura.subtract(totalCobrado);

        List<FacturaRefDTO> facturasRef = f.getDetallesFactura() != null
                ? f.getDetallesFactura().stream().map(df -> new FacturaRefDTO(
                        df.getNumeroPuntoDeVenta(),
                        df.getNumeroComprobante(),
                        df.getLetraComprobante(),
                        df.getTipoComprobante()
                )).toList()
                : List.of();

        return new DeudaClienteDuxDTO(
                f.getId(),
                f.getIdCliente(),
                clienteNombre,
                f.getCuit(),
                f.getTipoComp(),
                f.getLetraComp(),
                f.getNroComp(),
                f.getNroPtoVta(),
                f.getFecha(),
                parseBigDecimal(f.getTotal()),
                parseBigDecimal(f.getMontoGravado()),
                parseBigDecimal(f.getMontoIva()),
                parseBigDecimal(f.getMontoExento()),
                parseBigDecimal(f.getMontoDesc()),
                f.getAnuladaBoolean(),
                f.getUrlFactura(),
                vendedorNombre,
                totalCobrado,
                saldo,
                detalles,
                cobros,
                facturasRef
        );
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
