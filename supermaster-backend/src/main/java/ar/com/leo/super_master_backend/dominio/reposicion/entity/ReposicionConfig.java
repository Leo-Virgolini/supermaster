package ar.com.leo.super_master_backend.dominio.reposicion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reposicion_config", schema = "supermaster")
public class ReposicionConfig {

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "meses_cobertura", nullable = false)
    private Integer mesesCobertura = 2;

    @Column(name = "dias_por_periodo", nullable = false)
    private Integer diasPorPeriodo = 30;

    @Column(name = "peso_mes1", nullable = false, precision = 3, scale = 2)
    private BigDecimal pesoMes1 = new BigDecimal("0.50");

    @Column(name = "peso_mes2", nullable = false, precision = 3, scale = 2)
    private BigDecimal pesoMes2 = new BigDecimal("0.30");

    @Column(name = "peso_mes3", nullable = false, precision = 3, scale = 2)
    private BigDecimal pesoMes3 = new BigDecimal("0.20");

    @Column(name = "id_empresa_dux")
    private Integer idEmpresaDux;

    @Convert(converter = IntegerListConverter.class)
    @Column(name = "ids_sucursal_dux", length = 100)
    private List<Integer> idsSucursalDux = new ArrayList<>();

    @Column(name = "ultimo_stock_fetch")
    private LocalDateTime ultimoStockFetch;

    @Column(name = "ultimo_ventas_fetch")
    private LocalDate ultimoVentasFetch;
}
