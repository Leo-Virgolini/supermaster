package ar.com.leo.super_master_backend.apis.ml.service;

/** Utilidad compartida para convertir unidades de los atributos de paquete de Mercado Libre. */
public final class MlUnidades {

    private MlUnidades() {}

    /**
     * Convierte una dimensión lineal a centímetros.
     * Unidades soportadas: mm (÷10), cm (igual), m (×100).
     * Si {@code numero} es null devuelve null. Si {@code unit} es null o desconocida asume cm (devuelve el número tal cual).
     */
    public static Double aCm(Double numero, String unit) {
        if (numero == null) return null;
        if (unit == null) return numero;
        return switch (unit.toLowerCase()) {
            case "cm" -> numero;
            case "mm" -> numero / 10.0;
            case "m"  -> numero * 100.0;
            default   -> numero;
        };
    }

    /**
     * Convierte un peso a gramos.
     * Unidades soportadas: mg (÷1000), g (igual), kg (×1000).
     * Si {@code numero} es null devuelve null. Si {@code unit} es null o desconocida asume gramos (devuelve el número tal cual).
     */
    public static Double aGramos(Double numero, String unit) {
        if (numero == null) return null;
        if (unit == null) return numero;
        return switch (unit.toLowerCase()) {
            case "g"  -> numero;
            case "kg" -> numero * 1000.0;
            case "mg" -> numero / 1000.0;
            default   -> numero;
        };
    }
}
