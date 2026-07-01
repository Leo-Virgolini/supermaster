package ar.com.leo.super_master_backend.dominio.producto.estado;

import ar.com.leo.super_master_backend.apis.dux.model.Item;
import ar.com.leo.super_master_backend.apis.dux.model.Stock;
import ar.com.leo.super_master_backend.dominio.producto.estado.dto.EstadoCanalDTO;

import java.util.List;

/** Parsea un Item de Dux a EstadoCanalDTO (solo estado: Dux no aporta precio/stock comparables). */
public final class DuxEstadoParser {

    private DuxEstadoParser() {}

    public static EstadoCanalDTO parse(Item item) {
        if (item == null) return EstadoCanalDTO.noPublicado();
        boolean habilitado = "S".equalsIgnoreCase(item.getHabilitado());
        Integer stock = parseStock(item.getStock());
        return new EstadoCanalDTO(true, habilitado ? "habilitado" : "deshabilitado",
                null, null, stock, null, null, false, null, List.of());
    }

    private static Integer parseStock(List<Stock> stocks) {
        if (stocks == null || stocks.isEmpty()) {
            return null;
        }

        int total = 0;
        for (Stock s : stocks) {
            if (s == null || s.getStockDisponible() == null) {
                continue;
            }

            try {
                double value = Double.parseDouble(s.getStockDisponible().trim());
                total += Math.round(value);
            } catch (NumberFormatException e) {
                // Ignorar valores que no parsean
                continue;
            }
        }

        return total;
    }
}
