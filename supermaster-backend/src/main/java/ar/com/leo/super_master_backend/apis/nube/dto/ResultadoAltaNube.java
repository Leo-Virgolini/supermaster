package ar.com.leo.super_master_backend.apis.nube.dto;

public record ResultadoAltaNube(Estado estado, String motivo, Long productoNubeId, String advertencia) {
    public enum Estado { CREADO, YA_EXISTIA, ERROR }
    public static ResultadoAltaNube creado(Long productoNubeId) { return new ResultadoAltaNube(Estado.CREADO, null, productoNubeId, null); }
    public static ResultadoAltaNube yaExistia() { return new ResultadoAltaNube(Estado.YA_EXISTIA, null, null, null); }
    public static ResultadoAltaNube error(String motivo) { return new ResultadoAltaNube(Estado.ERROR, motivo, null, null); }

    /** Copia este resultado agregando una advertencia (p. ej. estado de las imágenes). */
    public ResultadoAltaNube conAdvertencia(String advertencia) {
        return new ResultadoAltaNube(estado, motivo, productoNubeId, advertencia);
    }
}
