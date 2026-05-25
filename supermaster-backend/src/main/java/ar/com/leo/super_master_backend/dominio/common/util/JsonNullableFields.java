package ar.com.leo.super_master_backend.dominio.common.util;

import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;

/**
 * Helpers para leer campos {@link JsonNullable} en métodos PATCH del dominio:
 * desempaqueta, valida tipo, valida rango básico y arroja {@link BadRequestException}
 * con mensaje estándar. Centralizado para no duplicar los ~6 helpers básicos en cada
 * uno de los ~20 services con PATCH del dominio.
 *
 * <p>Convenciones:
 * <ul>
 *   <li>{@link #presente}: usar al inicio de cada bloque {@code if (presente(dto.getX()))}
 *       para saber si el cliente envió el campo en el PATCH (presente && null = nullear).</li>
 *   <li>{@code leerXRequerido}: el campo debe estar presente y con tipo correcto; null tira.</li>
 *   <li>{@code leerXOpcional}: el campo puede ser null; devuelve null si lo es.</li>
 *   <li>{@link #valor} y {@link #normalizar}: helpers de bajo nivel para servicios que
 *       implementan validaciones de dominio específicas (e.g. {@code leerMargenRequerido}).</li>
 * </ul>
 *
 * <p>Validaciones específicas de dominio (porcentaje 0-100 vs -100..100, IVA, márgenes,
 * listas anidadas) NO van acá — quedan como helpers privados en el servicio que las usa,
 * porque los rangos válidos divergen entre dominios.
 */
public final class JsonNullableFields {

    private JsonNullableFields() {}

    // ============================================
    // Helpers básicos
    // ============================================

    public static boolean presente(JsonNullable<?> campo) {
        return campo == null || campo.isPresent();
    }

    public static Object valor(JsonNullable<?> campo) {
        return campo == null ? null : campo.orElse(null);
    }

    public static String normalizar(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // ============================================
    // String
    // ============================================

    public static String leerStringRequerido(JsonNullable<String> campo, String field, int maxLength) {
        Object value = valor(campo);
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser texto");
        }
        if (text.length() > maxLength) {
            throw new BadRequestException("El campo '" + field + "' no puede exceder " + maxLength + " caracteres");
        }
        return text;
    }

    public static String leerStringOpcional(JsonNullable<String> campo, String field, int maxLength) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new BadRequestException("El campo '" + field + "' debe ser texto");
        }
        if (text.length() > maxLength) {
            throw new BadRequestException("El campo '" + field + "' no puede exceder " + maxLength + " caracteres");
        }
        return text;
    }

    // ============================================
    // Boolean
    // ============================================

    public static Boolean leerBooleanRequerido(JsonNullable<Boolean> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Boolean bool)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser booleano");
        }
        return bool;
    }

    public static Boolean leerBooleanOpcional(JsonNullable<Boolean> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Boolean bool)) {
            throw new BadRequestException("El campo '" + field + "' debe ser booleano");
        }
        return bool;
    }

    // ============================================
    // Integer
    // ============================================

    public static Integer leerIntegerRequerido(JsonNullable<Integer> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        return number.intValue();
    }

    public static Integer leerIntegerOpcional(JsonNullable<Integer> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' debe ser numérico");
        }
        return number.intValue();
    }

    public static Integer leerIntegerNoNegativoRequerido(JsonNullable<Integer> campo, String field) {
        Integer value = leerIntegerRequerido(campo, field);
        if (value < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return value;
    }

    public static Integer leerIntegerNoNegativoOpcional(JsonNullable<Integer> campo, String field) {
        Integer value = leerIntegerOpcional(campo, field);
        if (value != null && value < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return value;
    }

    public static Integer leerIntegerPositivoOpcional(JsonNullable<Integer> campo, String field) {
        Integer value = leerIntegerOpcional(campo, field);
        if (value != null && value <= 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor a 0");
        }
        return value;
    }

    public static Integer leerIdRequerido(JsonNullable<Integer> campo, String field) {
        Integer value = leerIntegerRequerido(campo, field);
        if (value <= 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser positivo");
        }
        return value;
    }

    public static Integer leerIdOpcional(JsonNullable<Integer> campo, String field) {
        Integer value = leerIntegerOpcional(campo, field);
        if (value != null && value <= 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser positivo");
        }
        return value;
    }

    // ============================================
    // BigDecimal
    // ============================================

    public static BigDecimal leerDecimalRequerido(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' es requerido y debe ser numérico");
        }
        return new BigDecimal(number.toString());
    }

    public static BigDecimal leerDecimalOpcional(JsonNullable<BigDecimal> campo, String field) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw new BadRequestException("El campo '" + field + "' debe ser numérico");
        }
        return new BigDecimal(number.toString());
    }

    public static BigDecimal leerDecimalNoNegativoRequerido(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalRequerido(campo, field);
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return decimal;
    }

    public static BigDecimal leerDecimalNoNegativoOpcional(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalOpcional(campo, field);
        if (decimal != null && decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("El campo '" + field + "' debe ser mayor o igual a 0");
        }
        return decimal;
    }

    /** Porcentaje [0, 100]. Para porcentajes con signo (-100..100) cada service define el suyo. */
    public static BigDecimal leerPorcentajeRequerido(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalRequerido(campo, field);
        if (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre 0 y 100");
        }
        return decimal;
    }

    /** Porcentaje [0, 100]. Para porcentajes con signo (-100..100) cada service define el suyo. */
    public static BigDecimal leerPorcentajeOpcional(JsonNullable<BigDecimal> campo, String field) {
        BigDecimal decimal = leerDecimalOpcional(campo, field);
        if (decimal != null && (decimal.compareTo(BigDecimal.ZERO) < 0 || decimal.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BadRequestException("El campo '" + field + "' debe estar entre 0 y 100");
        }
        return decimal;
    }

    // ============================================
    // Enum
    // ============================================

    public static <E extends Enum<E>> E leerEnumOpcional(JsonNullable<E> campo, String field, Class<E> enumClass) {
        Object value = valor(campo);
        if (value == null) {
            return null;
        }
        if (!enumClass.isInstance(value)) {
            throw new BadRequestException("El campo '" + field + "' contiene un valor inválido");
        }
        return enumClass.cast(value);
    }

    public static <E extends Enum<E>> E leerEnumRequerido(JsonNullable<E> campo, String field, Class<E> enumClass) {
        Object value = valor(campo);
        if (value == null) {
            throw new BadRequestException("El campo '" + field + "' es requerido");
        }
        if (!enumClass.isInstance(value)) {
            throw new BadRequestException("Valor inválido para '" + field + "'");
        }
        return enumClass.cast(value);
    }
}
