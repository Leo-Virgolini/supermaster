package ar.com.leo.super_master_backend.dominio.producto.mapper;

import ar.com.leo.super_master_backend.config.GlobalMapperConfig;
import ar.com.leo.super_master_backend.dominio.clasif_gastro.entity.ClasifGastro;
import ar.com.leo.super_master_backend.dominio.clasif_gral.entity.ClasifGral;
import ar.com.leo.super_master_backend.dominio.marca.entity.Marca;
import ar.com.leo.super_master_backend.dominio.material.entity.Material;
import ar.com.leo.super_master_backend.dominio.origen.entity.Origen;
import ar.com.leo.super_master_backend.dominio.producto.dto.*;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecioInflado;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoMargen;
import ar.com.leo.super_master_backend.dominio.producto.mla.entity.Mla;
import ar.com.leo.super_master_backend.dominio.proveedor.entity.Proveedor;
import ar.com.leo.super_master_backend.dominio.tipo.entity.Tipo;
import ar.com.leo.super_master_backend.dominio.sector_deposito.entity.SectorDeposito;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mapper(config = GlobalMapperConfig.class, imports = {
        Marca.class,
        Origen.class,
        ClasifGral.class,
        ClasifGastro.class,
        Tipo.class,
        Proveedor.class,
        Material.class,
        Mla.class,
        SectorDeposito.class
})
public interface ProductoMapper {

    // ================================================================
    // ENTITY → DTO
    // ================================================================
    default ProductoDTO toDTO(Producto entity) {
        return toDTO(entity, null, null, null, null);
    }

    default ProductoDTO toDTO(Producto entity, List<String> aptos, List<String> catalogos, List<String> clientes) {
        return toDTO(entity, aptos, catalogos, clientes, null);
    }

    default ProductoDTO toDTO(Producto entity, List<String> aptos, List<String> catalogos, List<String> clientes, ProductoMargen margen) {
        if (entity == null) return null;
        return new ProductoDTO(
                entity.getId(),
                entity.getSku(),
                entity.getCodExt(),
                entity.getTituloDux(),
                entity.getTituloMl(),
                entity.getTituloNube(),
                entity.getEsCombo(),
                entity.getUxb(),
                entity.getMoq(),
                entity.getImagenUrl(),
                entity.getStock(),
                entity.getActivo(),
                entity.getTagReposicion(),
                entity.getTag(),
                entity.getMarca() != null ? entity.getMarca().getId() : null,
                entity.getOrigen() != null ? entity.getOrigen().getId() : null,
                entity.getClasifGral() != null ? entity.getClasifGral().getId() : null,
                entity.getClasifGastro() != null ? entity.getClasifGastro().getId() : null,
                entity.getTipo() != null ? entity.getTipo().getId() : null,
                entity.getProveedor() != null ? entity.getProveedor().getId() : null,
                entity.getMaterial() != null ? entity.getMaterial().getId() : null,
                entity.getMla() != null ? entity.getMla().getId() : null,
                entity.getMla() != null ? entity.getMla().getMla() : null,
                entity.getSectorDeposito() != null ? entity.getSectorDeposito().getId() : null,
                buildNombreCompleto(entity.getMarca()),
                buildNombreCompleto(entity.getTipo()),
                buildNombreCompleto(entity.getClasifGral()),
                buildNombreCompleto(entity.getClasifGastro()),
                entity.getCapacidad(),
                entity.getLargo(),
                entity.getAncho(),
                entity.getAlto(),
                entity.getDiamboca(),
                entity.getDiambase(),
                entity.getEspesor(),
                entity.getCosto(),
                entity.getFechaUltimoCosto(),
                entity.getIva(),
                entity.getFechaCreacion(),
                entity.getFechaModificacion(),
                aptos != null ? aptos : obtenerAptos(entity),
                catalogos != null ? catalogos : obtenerCatalogos(entity),
                clientes != null ? clientes : obtenerClientes(entity),
                margen != null ? margen.getMargenMinorista() : null,
                margen != null ? margen.getMargenMayorista() : null
        );
    }

    private List<String> obtenerAptos(Producto entity) {
        return entity.getProductosApto() != null
                ? entity.getProductosApto().stream()
                    .map(pa -> pa.getApto().getNombre())
                    .sorted()
                    .toList()
                : List.of();
    }

    private List<String> obtenerCatalogos(Producto entity) {
        return entity.getProductoCatalogos() != null
                ? entity.getProductoCatalogos().stream()
                    .map(pc -> pc.getCatalogo().getNombre())
                    .sorted()
                    .toList()
                : List.of();
    }

    private List<String> obtenerClientes(Producto entity) {
        return entity.getProductoClientes() != null
                ? entity.getProductoClientes().stream()
                    .map(pcl -> pcl.getCliente().getNombre())
                    .sorted()
                    .toList()
                : List.of();
    }

    // ================================================================
    // PATHS JERÁRQUICOS "ABUELO > PADRE > HIJO"
    // Navegan recursivamente la cadena de padres de cada entidad jerárquica.
    // ================================================================
    default String buildNombreCompleto(Marca m) {
        if (m == null) return null;
        if (m.getPadre() == null) return m.getNombre();
        return buildNombreCompleto(m.getPadre()) + " > " + m.getNombre();
    }

    default String buildNombreCompleto(Tipo t) {
        if (t == null) return null;
        if (t.getPadre() == null) return t.getNombre();
        return buildNombreCompleto(t.getPadre()) + " > " + t.getNombre();
    }

    default String buildNombreCompleto(ClasifGral c) {
        if (c == null) return null;
        if (c.getPadre() == null) return c.getNombre();
        return buildNombreCompleto(c.getPadre()) + " > " + c.getNombre();
    }

    default String buildNombreCompleto(ClasifGastro c) {
        if (c == null) return null;
        if (c.getPadre() == null) return c.getNombre();
        return buildNombreCompleto(c.getPadre()) + " > " + c.getNombre();
    }

    // ================================================================
    // DTO CREATE → ENTITY
    // ================================================================
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "marca", expression = "java(dto.marcaId() != null ? new Marca(dto.marcaId()) : null)")
    @Mapping(target = "origen", expression = "java(dto.origenId() != null ? new Origen(dto.origenId()) : null)")
    @Mapping(target = "clasifGral", expression = "java(dto.clasifGralId() != null ? new ClasifGral(dto.clasifGralId()) : null)")
    @Mapping(target = "clasifGastro", expression = "java(dto.clasifGastroId() != null ? new ClasifGastro(dto.clasifGastroId()) : null)")
    @Mapping(target = "tipo", expression = "java(new Tipo(dto.tipoId()))")
    @Mapping(target = "proveedor", expression = "java(dto.proveedorId() != null ? new Proveedor(dto.proveedorId()) : null)")
    @Mapping(target = "material", expression = "java(dto.materialId() != null ? new Material(dto.materialId()) : null)")
    @Mapping(target = "mla", expression = "java(mapMla(dto.mlaId()))")
    @Mapping(target = "sectorDeposito", expression = "java(dto.sectorDepositoId() != null ? new SectorDeposito(dto.sectorDepositoId()) : null)")
    @Mapping(target = "fechaCreacion", ignore = true)     // se setea en @PrePersist
    @Mapping(target = "fechaModificacion", ignore = true)
    // se setea en @PreUpdate
    Producto toEntity(ProductoCreateDTO dto);

    // ================================================================
    // DTO UPDATE → ENTITY (solo patch, ignora nulls)
    // ================================================================
    @Mapping(target = "marca", expression = "java(dto.marcaId() != null ? new Marca(dto.marcaId()) : entity.getMarca())")
    @Mapping(target = "origen", expression = "java(dto.origenId() != null ? new Origen(dto.origenId()) : entity.getOrigen())")
    @Mapping(target = "clasifGral", expression = "java(dto.clasifGralId() != null ? new ClasifGral(dto.clasifGralId()) : entity.getClasifGral())")
    @Mapping(target = "clasifGastro", expression = "java(dto.clasifGastroId() != null ? new ClasifGastro(dto.clasifGastroId()) : entity.getClasifGastro())")
    @Mapping(target = "tipo", expression = "java(dto.tipoId() != null ? new Tipo(dto.tipoId()) : entity.getTipo())")
    @Mapping(target = "proveedor", expression = "java(dto.proveedorId() != null ? new Proveedor(dto.proveedorId()) : entity.getProveedor())")
    @Mapping(target = "material", expression = "java(dto.materialId() != null ? new Material(dto.materialId()) : entity.getMaterial())")
    @Mapping(target = "mla", expression = "java(dto.mlaId() != null ? mapMla(dto.mlaId()) : entity.getMla())")
    @Mapping(target = "sectorDeposito", expression = "java(dto.sectorDepositoId() != null ? new SectorDeposito(dto.sectorDepositoId()) : entity.getSectorDeposito())")
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaModificacion", ignore = true)
    void updateEntityFromDTO(ProductoUpdateDTO dto, @MappingTarget Producto entity);

    default Mla mapMla(Integer mlaId) {
        if (mlaId == null) {
            return null;
        }
        Mla mla = new Mla();
        mla.setId(mlaId);
        return mla;
    }

    // ================================================================
    // RESUMEN PARA LISTADOS (si lo necesitás)
    // ================================================================
    @Named("toResumen")
    ProductoResumenDTO toResumenDTO(Producto entity);

    // ================================================================
    // PRODUCTO CON PRECIOS POR CANAL
    // ================================================================

    /**
     * Versión que usa descripciones por defecto (para compatibilidad).
     */
    default ProductoConPreciosDTO toProductoConPreciosDTO(Producto producto, ProductoMargen productoMargen, List<ProductoCanalPrecio> precios) {
        return toProductoConPreciosDTO(producto, productoMargen, precios, null, null, null);
    }

    /**
     * Versión que usa descripciones de canal_concepto_cuota.
     * @param descripcionesCuotas Mapa de (canalId + "_" + cuotas) -> descripcion
     */
    default ProductoConPreciosDTO toProductoConPreciosDTO(Producto producto, ProductoMargen productoMargen, List<ProductoCanalPrecio> precios, Map<String, String> descripcionesCuotas) {
        return toProductoConPreciosDTO(producto, productoMargen, precios, descripcionesCuotas, null, null);
    }

    /**
     * Versión completa que incluye descuentos aplicables por canal.
     * @param descripcionesCuotas Mapa de (canalId + "_" + cuotas) -> descripcion
     * @param descuentosPorCanal Mapa de canalId -> lista de descuentos aplicables (puede ser null)
     */
    default ProductoConPreciosDTO toProductoConPreciosDTO(
            Producto producto,
            ProductoMargen productoMargen,
            List<ProductoCanalPrecio> precios,
            Map<String, String> descripcionesCuotas,
            Map<Integer, List<DescuentoAplicableDTO>> descuentosPorCanal) {
        return toProductoConPreciosDTO(producto, productoMargen, precios, descripcionesCuotas, descuentosPorCanal, null);
    }

    /**
     * Versión completa que incluye nombres de canales explícitos.
     */
    default ProductoConPreciosDTO toProductoConPreciosDTO(
            Producto producto,
            ProductoMargen productoMargen,
            List<ProductoCanalPrecio> precios,
            Map<String, String> descripcionesCuotas,
            Map<Integer, List<DescuentoAplicableDTO>> descuentosPorCanal,
            Map<Integer, String> nombresPorCanal) {
        return toProductoConPreciosDTO(producto, productoMargen, precios, descripcionesCuotas, descuentosPorCanal, nombresPorCanal, null);
    }

    /**
     * Versión completa que incluye nombres de canales y reglas de inflado.
     * @param infladosPorProductoCanal Mapa de "productoId_canalId" -> ProductoCanalPrecioInflado activo (puede ser null)
     */
    default ProductoConPreciosDTO toProductoConPreciosDTO(
            Producto producto,
            ProductoMargen productoMargen,
            List<ProductoCanalPrecio> precios,
            Map<String, String> descripcionesCuotas,
            Map<Integer, List<DescuentoAplicableDTO>> descuentosPorCanal,
            Map<Integer, String> nombresPorCanal,
            Map<String, ProductoCanalPrecioInflado> infladosPorProductoCanal) {
        // Obtener MLA (si existe)
        Mla mlaEntity = producto.getMla();
        String mla = mlaEntity != null ? mlaEntity.getMla() : null;
        String mlau = mlaEntity != null ? mlaEntity.getMlau() : null;
        BigDecimal precioEnvio = mlaEntity != null ? mlaEntity.getPrecioEnvio() : null;
        LocalDateTime fechaCalculoEnvio = mlaEntity != null ? mlaEntity.getFechaCalculoEnvio() : null;
        BigDecimal comisionPorcentaje = mlaEntity != null ? mlaEntity.getComisionPorcentaje() : null;

        // Obtener márgenes (si existen)
        BigDecimal margenMinorista = productoMargen != null ? productoMargen.getMargenMinorista() : null;
        BigDecimal margenMayorista = productoMargen != null ? productoMargen.getMargenMayorista() : null;

        // Agrupar precios por canal
        Map<Integer, List<ProductoCanalPrecio>> preciosPorCanal = precios.stream()
                .collect(Collectors.groupingBy(pcp -> pcp.getCanal().getId()));

        List<CanalPreciosDTO> preciosCanales = preciosPorCanal.entrySet().stream()
                .map(entry -> {
                    Integer canalId = entry.getKey();
                    List<ProductoCanalPrecio> preciosDelCanal = entry.getValue();
                    ProductoCanalPrecio primerPrecio = preciosDelCanal.get(0);

                    // Obtener descuentos para este canal (si existen)
                    List<DescuentoAplicableDTO> descuentosCanal = descuentosPorCanal != null
                            ? descuentosPorCanal.get(canalId)
                            : null;

                    // Obtener regla de inflado para este producto+canal (si existe)
                    String infCodigo = null;
                    String infTipo = null;
                    BigDecimal infValor = null;
                    if (infladosPorProductoCanal != null) {
                        String infKey = producto.getId() + "_" + canalId;
                        ProductoCanalPrecioInflado pcpi = infladosPorProductoCanal.get(infKey);
                        if (pcpi != null && pcpi.getPrecioInflado() != null) {
                            infCodigo = pcpi.getPrecioInflado().getCodigo();
                            infTipo = pcpi.getPrecioInflado().getTipo().name();
                            infValor = pcpi.getPrecioInflado().getValor();
                        }
                    }
                    final String finalInfCodigo = infCodigo;
                    final String finalInfTipo = infTipo;
                    final BigDecimal finalInfValor = infValor;

                    List<PrecioDTO> preciosList = preciosDelCanal.stream()
                            .map(pcp -> new PrecioDTO(
                                    pcp.getCuotas(),
                                    obtenerDescripcionCuota(pcp.getCanal().getId(), pcp.getCuotas(), descripcionesCuotas),
                                    pcp.getPvp(),
                                    pcp.getPvpInflado(),
                                    pcp.getCostoProducto(),
                                    pcp.getCostosVenta(),
                                    pcp.getIngresoNetoVendedor(),
                                    pcp.getGanancia(),
                                    pcp.getMargenSobreIngresoNeto(),
                                    pcp.getMargenSobrePvp(),
                                    pcp.getMarkupPorcentaje(),
                                    pcp.getFechaUltimoCalculo(),
                                    descuentosCanal,
                                    finalInfCodigo,
                                    finalInfTipo,
                                    finalInfValor
                            ))
                            .toList();

                    // Obtener nombre del canal: primero del mapa explícito, luego de la entidad
                    String canalNombre = nombresPorCanal != null
                            ? nombresPorCanal.get(canalId)
                            : primerPrecio.getCanal().getNombre();

                    return new CanalPreciosDTO(
                            canalId,
                            canalNombre,
                            preciosList
                    );
                })
                .sorted((a, b) -> a.canalId().compareTo(b.canalId()))
                .toList();

        return new ProductoConPreciosDTO(
                // Identificación
                producto.getId(),
                producto.getSku(),

                // MLA
                mla,
                mlau,
                precioEnvio,
                fechaCalculoEnvio,
                comisionPorcentaje,

                producto.getCodExt(),
                producto.getTituloDux(),
                producto.getTituloMl(),
                producto.getTituloNube(),
                producto.getEsCombo(),
                producto.getClasifGastro() != null ? producto.getClasifGastro().getEsMaquina() : null,
                producto.getImagenUrl(),
                producto.getStock(),
                producto.getActivo(),
                producto.getTagReposicion(),
                producto.getTag(),

                // Relaciones (nombres)
                producto.getMarca() != null ? producto.getMarca().getNombre() : null,
                producto.getOrigen() != null ? producto.getOrigen().getNombre() : null,
                producto.getClasifGral() != null ? producto.getClasifGral().getNombre() : null,
                producto.getClasifGastro() != null ? producto.getClasifGastro().getNombre() : null,
                producto.getTipo() != null ? producto.getTipo().getNombre() : null,
                producto.getProveedor() != null ? producto.getProveedor().getNombre() : null,
                producto.getMaterial() != null ? producto.getMaterial().getNombre() : null,

                // Dimensiones y atributos
                producto.getUxb(),
                producto.getMoq(),
                producto.getCapacidad(),
                producto.getLargo(),
                producto.getAncho(),
                producto.getAlto(),
                producto.getDiamboca(),
                producto.getDiambase(),
                producto.getEspesor(),

                // Precios y costos
                producto.getCosto(),
                producto.getFechaUltimoCosto(),
                producto.getIva(),
                producto.getSectorDeposito() != null ? producto.getSectorDeposito().getId() : null,

                // Márgenes
                margenMinorista,
                margenMayorista,

                // Fechas
                producto.getFechaCreacion(),
                producto.getFechaModificacion(),

                // Many-to-many (nombres)
                producto.getProductosApto() != null
                        ? producto.getProductosApto().stream()
                            .map(pa -> pa.getApto().getNombre())
                            .sorted()
                            .toList()
                        : List.of(),
                producto.getProductoCatalogos() != null
                        ? producto.getProductoCatalogos().stream()
                            .map(pc -> pc.getCatalogo().getNombre())
                            .sorted()
                            .toList()
                        : List.of(),
                producto.getProductoClientes() != null
                        ? producto.getProductoClientes().stream()
                            .map(pcl -> pcl.getCliente().getNombre())
                            .sorted()
                            .toList()
                        : List.of(),

                // Precios por canal
                preciosCanales
        );
    }

    /**
     * Obtiene la descripción de la cuota del mapa o genera una por defecto.
     */
    default String obtenerDescripcionCuota(Integer canalId, Integer cuotas, Map<String, String> descripcionesCuotas) {
        if (descripcionesCuotas != null && canalId != null && cuotas != null) {
            String key = canalId + "_" + cuotas;
            String descripcion = descripcionesCuotas.get(key);
            if (descripcion != null && !descripcion.isBlank()) {
                return descripcion;
            }
        }
        return null;
    }
}
