package ar.com.leo.super_master_backend.apis.ml.dto;

public record ResultadoAltaMl(Estado estado, String motivo, String itemId, String mlau, String advertencia) {
    public enum Estado { CREADO, ACTUALIZADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaMl creado(String itemId, String mlau) { return new ResultadoAltaMl(Estado.CREADO, null, itemId, mlau, null); }
    public static ResultadoAltaMl actualizado(String itemId) { return new ResultadoAltaMl(Estado.ACTUALIZADO, null, itemId, null, null); }
    public static ResultadoAltaMl yaExistia() { return new ResultadoAltaMl(Estado.YA_EXISTIA, null, null, null, null); }
    public static ResultadoAltaMl error(String motivo) { return new ResultadoAltaMl(Estado.ERROR, motivo, null, null, null); }
    public ResultadoAltaMl conAdvertencia(String advertencia) {
        return new ResultadoAltaMl(estado, motivo, itemId, mlau, advertencia);
    }
}
