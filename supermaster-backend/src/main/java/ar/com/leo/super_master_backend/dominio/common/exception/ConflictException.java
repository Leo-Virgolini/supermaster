package ar.com.leo.super_master_backend.dominio.common.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String mensaje) {
        super(mensaje);
    }
}