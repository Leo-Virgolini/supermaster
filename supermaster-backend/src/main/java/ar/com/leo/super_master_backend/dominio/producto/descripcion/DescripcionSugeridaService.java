package ar.com.leo.super_master_backend.dominio.producto.descripcion;

import ar.com.leo.super_master_backend.apis.ml.service.MlDescripcionSugeridaBuilder;
import ar.com.leo.super_master_backend.apis.nube.service.NubeDescripcionSugeridaBuilder;
import ar.com.leo.super_master_backend.dominio.common.exception.BadRequestException;
import ar.com.leo.super_master_backend.dominio.common.exception.NotFoundException;
import ar.com.leo.super_master_backend.dominio.producto.descripcion.dto.DescripcionSugeridaDTO;
import ar.com.leo.super_master_backend.dominio.producto.entity.Producto;
import ar.com.leo.super_master_backend.dominio.producto.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DescripcionSugeridaService {

    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public DescripcionSugeridaDTO sugerir(Integer productoId, String canal) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        String c = canal == null ? "" : canal.toLowerCase();
        return switch (c) {
            case "ml" -> new DescripcionSugeridaDTO(MlDescripcionSugeridaBuilder.construir(p));
            case "nube" -> new DescripcionSugeridaDTO(NubeDescripcionSugeridaBuilder.construir(p));
            default -> throw new BadRequestException("canal inválido (usar 'ml' o 'nube')");
        };
    }
}
