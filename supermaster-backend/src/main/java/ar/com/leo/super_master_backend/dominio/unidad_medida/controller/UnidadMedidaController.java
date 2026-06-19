package ar.com.leo.super_master_backend.dominio.unidad_medida.controller;

import ar.com.leo.super_master_backend.dominio.unidad_medida.dto.UnidadMedidaDTO;
import ar.com.leo.super_master_backend.dominio.unidad_medida.repository.UnidadMedidaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadMedidaController {

    private final UnidadMedidaRepository repository;

    public UnidadMedidaController(UnidadMedidaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<UnidadMedidaDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        var pageable = PageRequest.of(page, size, Sort.by("codigo").ascending());
        var resultado = search.isBlank()
                ? repository.findAll(pageable)
                : repository.findByCodigoContainingIgnoreCase(search, pageable);
        return resultado.map(u -> new UnidadMedidaDTO(u.getId(), u.getCodigo()));
    }
}
