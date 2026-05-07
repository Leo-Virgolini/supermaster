package ar.com.leo.super_master_backend.dominio.common.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        boolean success,
        String message,
        String path,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String msg, String path) {
        return new ErrorResponse(false, msg, path, LocalDateTime.now());
    }
}