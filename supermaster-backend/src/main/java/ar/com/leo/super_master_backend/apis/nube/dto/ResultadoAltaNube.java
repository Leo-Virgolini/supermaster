package ar.com.leo.super_master_backend.apis.nube.dto;

public record ResultadoAltaNube(Estado estado, String motivo) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaNube creado() { return new ResultadoAltaNube(Estado.CREADO, null); }
    public static ResultadoAltaNube yaExistia() { return new ResultadoAltaNube(Estado.YA_EXISTIA, null); }
    public static ResultadoAltaNube error(String motivo) { return new ResultadoAltaNube(Estado.ERROR, motivo); }
}
