package ar.com.leo.super_master_backend.dominio.producto.dto;

import ar.com.leo.super_master_backend.dominio.producto.entity.Tag;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductoConPreciosDTO(
        // Identificación
        Integer id,
        String sku,

        // MLA
        String mla,
        String mlau,
        BigDecimal precioEnvio,
        LocalDateTime fechaCalculoEnvio,
        BigDecimal comisionPorcentaje,

        String codExt,
        String tituloDux,
        String tituloNube,
        Boolean esCombo,
        Boolean esMaquina,
        Integer stock,
        Boolean activo,
        TagReposicion tagReposicion,
        Tag tag,

        // Relaciones (nombres)
        String marcaNombre,
        String origenNombre,
        String clasifGralNombre,
        String clasifGastroNombre,
        String tipoNombre,
        String proveedorNombre,
        String materialNombre,

        // Dimensiones y atributos
        Integer uxb,
        Integer moq,
        String capacidad,
        String largo,
        String ancho,
        String alto,
        String diamboca,
        String diambase,
        String espesor,

        // Precios y costos
        BigDecimal costo,
        LocalDateTime fechaUltimoCosto,
        BigDecimal iva,
        Integer sectorDepositoId,

        // Márgenes (de producto_margen)
        BigDecimal margenMinorista,
        BigDecimal margenMayorista,

        // Fechas
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModificacion,

        // Many-to-many (nombres)
        List<String> aptos,
        List<String> catalogos,
        List<String> segmentos,

        // Canales con sus precios
        List<CanalPreciosDTO> canales,

        // Dimensiones del paquete de envío para Mercado Libre
        BigDecimal mlPaqAlto,
        BigDecimal mlPaqAncho,
        BigDecimal mlPaqLargo,
        BigDecimal mlPaqPeso
) {
}
