package ar.com.leo.super_master_backend.dominio.common.exception;

/**
 * Excepción lanzada cuando un servicio externo no está configurado correctamente.
 * Por ejemplo: credenciales faltantes, tokens no disponibles, etc.
 *
 * Resulta en HTTP 503 Service Unavailable.
 */
public class ServiceNotConfiguredException extends RuntimeException {

    private final String servicio;

    public ServiceNotConfiguredException(String servicio, String mensaje) {
        super(mensaje);
        this.servicio = servicio;
    }

    public String getServicio() {
        return servicio;
    }
}
