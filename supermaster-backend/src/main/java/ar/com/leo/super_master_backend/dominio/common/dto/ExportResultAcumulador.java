package ar.com.leo.super_master_backend.dominio.common.dto;

import java.util.ArrayList;
import java.util.List;

/** Acumula el resultado de exportar/sincronizar productos a un canal y lo arma en un ExportCanalResultDTO. */
public class ExportResultAcumulador {
    private int creados = 0;
    private final List<String> actualizados = new ArrayList<>();
    private final List<String> yaExistian = new ArrayList<>();
    private final List<String> errores = new ArrayList<>();
    private final List<String> advertencias = new ArrayList<>();

    public void creado() { creados++; }
    public void actualizado(String etiqueta) { actualizados.add(etiqueta); }
    public void yaExistia(String etiqueta) { yaExistian.add(etiqueta); }
    public void error(String detalle) { errores.add(detalle); }
    public void advertencia(String detalle) { advertencias.add(detalle); }

    public ExportCanalResultDTO toDTO() {
        return new ExportCanalResultDTO(creados, actualizados, yaExistian, errores, advertencias);
    }

    /** Normaliza SKUs de un request: no nulos/blancos, trim, distinct. */
    public static List<String> normalizarSkus(List<String> skus) {
        return skus.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
    }
}
