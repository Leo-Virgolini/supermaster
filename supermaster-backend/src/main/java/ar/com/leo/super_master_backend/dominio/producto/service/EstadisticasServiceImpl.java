package ar.com.leo.super_master_backend.dominio.producto.service;

import ar.com.leo.super_master_backend.dominio.canal.repository.CanalConceptoCuotaRepository;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO;
import ar.com.leo.super_master_backend.dominio.producto.dto.EstadisticasDTO.*;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.entity.ProductoCanalPrecio;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoCanalPrecioRepository;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import ar.com.leo.super_master_backend.dominio.imagen.service.ImagenService;
import ar.com.leo.super_master_backend.dominio.reposicion.entity.TagReposicion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstadisticasServiceImpl implements EstadisticasService {

    private final ProductoRepository productoRepository;
    private final ProductoCanalPrecioRepository precioRepository;
    private final CanalConceptoCuotaRepository canalConceptoCuotaRepository;
    private final ImagenService imagenService;

    @Override
    @Transactional(readOnly = true)
    public EstadisticasDTO obtenerEstadisticas() {
        // Trae proveedor + productoCatalogos en una sola query: necesario porque
        // calcularProductosPorCatalogo itera producto.getProductoCatalogos().
        List<Producto> productos = productoRepository.findAllWithProveedorYCatalogos();
        List<ProductoCanalPrecio> precios = precioRepository.findAllWithCanalAndProducto();
        ResumenDTO resumen = construirResumen(productos, precios);
        List<ProductosPorCatalogo> productosPorCatalogo = calcularProductosPorCatalogo(productos);
        List<ProductosPorProveedor> productosPorProveedor = calcularProductosPorProveedor(productos);
        List<ProductoMargenNegativo> productosConMargenNegativo = calcularProductosConMargenNegativo(precios);

        return new EstadisticasDTO(
                resumen.totalProductos(),
                resumen.productosActivos(),
                resumen.productosSinStock(),
                resumen.productosSinCosto(),
                resumen.productosSinMargen(),
                resumen.productosMargenNegativo(),
                productosPorCatalogo,
                productosPorProveedor,
                productosConMargenNegativo,
                resumen.cuotasDisponibles()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenDTO obtenerResumen() {
        List<Producto> productos = productoRepository.findAllWithProveedor();
        List<ProductoCanalPrecio> precios = precioRepository.findAllWithCanalAndProducto();
        return construirResumen(productos, precios);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuotaDisponibleDTO> obtenerCuotasDisponibles() {
        return calcularCuotasDisponiblesConfiguradas();
    }

    @Override
    @Transactional(readOnly = true)
    public MargenesPorCuotasDTO obtenerMargenesPorCuotas(Integer cuotas) {
        List<ProductoCanalPrecio> precios = precioRepository.findAllWithCanalAndProducto();
        List<MargenPorCanal> margenesPorCanal = calcularMargenesPorCanal(precios, cuotas);
        DistribucionMargenes distribucionMargenes = calcularDistribucion(precios, cuotas);
        return new MargenesPorCuotasDTO(margenesPorCanal, distribucionMargenes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductosPorProveedor> obtenerProductosPorProveedor() {
        List<Producto> productos = productoRepository.findAllWithProveedor();
        return calcularProductosPorProveedor(productos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductosPorCatalogo> obtenerProductosPorCatalogo() {
        List<Producto> productos = productoRepository.findAllWithProveedorYCatalogos();
        return calcularProductosPorCatalogo(productos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductoMargenNegativo> obtenerProductosConMargenNegativo() {
        List<ProductoCanalPrecio> precios = precioRepository.findAllWithCanalAndProducto();
        return calcularProductosConMargenNegativo(precios);
    }

    private List<MargenPorCanal> calcularMargenesPorCanal(List<ProductoCanalPrecio> precios, Integer cuotas) {
        Map<Integer, List<ProductoCanalPrecio>> porCanal = precios.stream()
                .filter(p -> coincideCuotas(p, cuotas))
                .filter(p -> p.getMargenSobrePvp() != null)
                .collect(Collectors.groupingBy(p -> p.getCanal().getId()));

        return porCanal.entrySet().stream()
                .map(entry -> {
                    List<ProductoCanalPrecio> lista = entry.getValue();
                    String nombre = lista.getFirst().getCanal().getNombre();

                    BigDecimal margenProm = promedio(lista.stream().map(ProductoCanalPrecio::getMargenSobrePvp).toList());
                    BigDecimal margenPromSobreIngresoNeto = promedio(lista.stream().map(ProductoCanalPrecio::getMargenSobreIngresoNeto).filter(Objects::nonNull).toList());
                    BigDecimal markupProm = promedio(lista.stream().map(ProductoCanalPrecio::getMarkupPorcentaje).filter(Objects::nonNull).toList());
                    BigDecimal gananciaProm = promedio(lista.stream().map(ProductoCanalPrecio::getGanancia).filter(Objects::nonNull).toList());

                    return new MargenPorCanal(entry.getKey(), nombre, margenProm, margenPromSobreIngresoNeto, markupProm, gananciaProm, lista.size());
                })
                .sorted(Comparator.comparing(MargenPorCanal::canalNombre))
                .toList();
    }

    private List<ProductosPorCatalogo> calcularProductosPorCatalogo(List<Producto> productos) {
        Map<Integer, List<Producto>> porCatalogo = productos.stream()
                .flatMap(producto -> producto.getProductoCatalogos().stream()
                        .filter(pc -> pc.getCatalogo() != null)
                        .map(pc -> Map.entry(pc.getCatalogo().getId(), producto)))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        return porCatalogo.entrySet().stream()
                .map(entry -> {
                    List<Producto> productosDelCatalogo = entry.getValue();
                    String nombre = productosDelCatalogo.getFirst()
                            .getProductoCatalogos()
                            .stream()
                            .filter(pc -> pc.getCatalogo() != null && Objects.equals(pc.getCatalogo().getId(), entry.getKey()))
                            .map(pc -> pc.getCatalogo().getNombre())
                            .findFirst()
                            .orElse("Sin catalogo");
                    int cantidadProductos = (int) productosDelCatalogo.stream()
                            .map(Producto::getId)
                            .distinct()
                            .count();
                    return new ProductosPorCatalogo(entry.getKey(), nombre, cantidadProductos);
                })
                .sorted(Comparator.comparing(ProductosPorCatalogo::cantidad).reversed())
                .toList();
    }

    private List<ProductoMargenNegativo> calcularProductosConMargenNegativo(List<ProductoCanalPrecio> precios) {
        return precios.stream()
                .filter(p -> p.getGanancia() != null && p.getGanancia().compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(ProductoCanalPrecio::getGanancia))
                .limit(50)
                .map(p -> new ProductoMargenNegativo(
                        p.getProducto().getId(),
                        p.getProducto().getSku(),
                        p.getProducto().getTituloDux(),
                        p.getCanal().getNombre(),
                        p.getCuotas(),
                        p.getMargenSobrePvp(),
                        p.getGanancia()
                ))
                .toList();
    }

    private DistribucionMargenes calcularDistribucion(List<ProductoCanalPrecio> precios, Integer cuotas) {
        List<BigDecimal> margenes = precios.stream()
                .filter(p -> coincideCuotas(p, cuotas))
                .map(ProductoCanalPrecio::getMargenSobrePvp)
                .filter(Objects::nonNull)
                .toList();

        int negativo = 0, r0a10 = 0, r10a20 = 0, r20a30 = 0, r30a50 = 0, rMayor50 = 0;
        for (BigDecimal m : margenes) {
            double v = m.doubleValue();
            if (v < 0) negativo++;
            else if (v < 10) r0a10++;
            else if (v < 20) r10a20++;
            else if (v < 30) r20a30++;
            else if (v < 50) r30a50++;
            else rMayor50++;
        }
        return new DistribucionMargenes(negativo, r0a10, r10a20, r20a30, r30a50, rMayor50);
    }

    private List<ProductosPorProveedor> calcularProductosPorProveedor(List<Producto> productos) {
        Map<Integer, List<Producto>> porProveedor = productos.stream()
                .filter(p -> p.getProveedor() != null)
                .collect(Collectors.groupingBy(p -> p.getProveedor().getId()));

        return porProveedor.entrySet().stream()
                .map(entry -> {
                    String nombre = entry.getValue().getFirst().getProveedor().getApodo();
                    return new ProductosPorProveedor(entry.getKey(), nombre, entry.getValue().size());
                })
                .sorted(Comparator.comparing(ProductosPorProveedor::cantidad).reversed())
                .limit(10)
                .toList();
    }

    private static boolean coincideCuotas(ProductoCanalPrecio p, Integer cuotas) {
        int cuotasPrecio = p.getCuotas() == null ? 0 : p.getCuotas();
        int cuotasFiltro = cuotas == null ? 0 : cuotas;
        return cuotasPrecio == cuotasFiltro;
    }

    private BigDecimal promedio(List<BigDecimal> valores) {
        if (valores.isEmpty()) return BigDecimal.ZERO;
        BigDecimal suma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(valores.size()), 2, RoundingMode.HALF_UP);
    }

    private ResumenDTO construirResumen(List<Producto> productos, List<ProductoCanalPrecio> precios) {
        int totalProductos = productos.size();
        int productosActivos = (int) productos.stream().filter(p -> Boolean.TRUE.equals(p.getActivo())).count();
        int productosSinStock = (int) productos.stream().filter(p -> p.getStock() == null || p.getStock() <= 0).count();
        int productosSinCosto = (int) productos.stream().filter(p -> p.getCosto() == null || p.getCosto().compareTo(BigDecimal.ZERO) <= 0).count();

        Set<Integer> idsConPrecio = precios.stream()
                .map(p -> p.getProducto().getId())
                .collect(Collectors.toSet());
        int productosSinMargen = (int) productos.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .filter(p -> !idsConPrecio.contains(p.getId()))
                .count();

        int productosMargenNegativo = (int) precios.stream()
                .filter(p -> p.getGanancia() != null && p.getGanancia().compareTo(BigDecimal.ZERO) < 0)
                .map(p -> p.getProducto().getId())
                .distinct()
                .count();

        int productosConMla = (int) productos.stream().filter(p -> p.getMla() != null).count();
        int productosSinProveedor = (int) productos.stream().filter(p -> p.getProveedor() == null).count();
        int productosSinImagen = (int) productos.stream().filter(p -> imagenService.resolverArchivoPorSku(p.getSku()) == null).count();
        int productosCombos = (int) productos.stream().filter(p -> Boolean.TRUE.equals(p.getEsCombo())).count();
        int productosPrio = (int) productos.stream().filter(p -> p.getTagReposicion() == TagReposicion.PRIO).count();
        int productosConPrecio = idsConPrecio.size();

        return new ResumenDTO(
                totalProductos,
                productosActivos,
                productosSinStock,
                productosSinCosto,
                productosSinMargen,
                productosMargenNegativo,
                productosConMla,
                productosSinProveedor,
                productosSinImagen,
                productosCombos,
                productosPrio,
                productosConPrecio,
                calcularValoresCuotasDisponibles()
        );
    }

    private List<CuotaDisponibleDTO> calcularCuotasDisponiblesConfiguradas() {
        return canalConceptoCuotaRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCuotas() == null ? 0 : c.getCuotas(),
                        TreeMap::new,
                        Collectors.mapping(
                                c -> c.getDescripcion() == null ? "" : c.getDescripcion().trim(),
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ))
                .entrySet().stream()
                .map(entry -> new CuotaDisponibleDTO(
                        entry.getKey(),
                        entry.getValue().stream()
                                .filter(desc -> !desc.isBlank())
                                .findFirst()
                                .orElse(null)
                ))
                .toList();
    }

    private List<Integer> calcularValoresCuotasDisponibles() {
        return calcularCuotasDisponiblesConfiguradas().stream()
                .map(CuotaDisponibleDTO::cuotas)
                .toList();
    }
}

