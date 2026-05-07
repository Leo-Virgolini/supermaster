package ar.com.leo.super_master_backend.dominio.reposicion.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record ReposicionConfigDTO(
        @Positive(message = "Los meses de cobertura deben ser positivos")
        Integer mesesCobertura,

        @Positive(message = "Los días por período deben ser positivos")
        Integer diasPorPeriodo,

        @DecimalMin(value = "0.0", message = "El peso del mes 1 debe ser >= 0")
        @DecimalMax(value = "1.0", message = "El peso del mes 1 debe ser <= 1")
        BigDecimal pesoMes1,

        @DecimalMin(value = "0.0", message = "El peso del mes 2 debe ser >= 0")
        @DecimalMax(value = "1.0", message = "El peso del mes 2 debe ser <= 1")
        BigDecimal pesoMes2,

        @DecimalMin(value = "0.0", message = "El peso del mes 3 debe ser >= 0")
        @DecimalMax(value = "1.0", message = "El peso del mes 3 debe ser <= 1")
        BigDecimal pesoMes3,

        @Positive(message = "El ID de empresa DUX debe ser positivo")
        Integer idEmpresaDux,

        List<@Positive(message = "Cada ID de sucursal debe ser positivo") Integer> idsSucursalDux
) {
}
