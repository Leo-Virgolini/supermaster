package ar.com.leo.super_master_backend.apis.ml.dto;

public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String advertencia) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaMl creado(String itemId) { return new ResultadoAltaMl(Estado.CREADO, null, itemId, null); }
    public static ResultadoAltaMl yaExistia() { return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null); }
    public static ResultadoAltaMl error(String motivo) { return new ResultadoAltaMl(Estado.ERROR, motivo, null, null); }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, advertencia);
    }
}
