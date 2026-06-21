package ar.com.leo.super_master_backend.dominio.sector_deposito.controller;

import ar.com.leo.super_master_backend.dominio.sector_deposito.dto.SectorDepositoDTO;
import ar.com.leo.super_master_backend.dominio.sector_deposito.repository.SectorDepositoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sectores-deposito")
public class SectorDepositoController {

    private final SectorDepositoRepository repository;

    public SectorDepositoController(SectorDepositoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<SectorDepositoDTO> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {
        var pageable = PageRequest.of(page, size, Sort.by("codigo").ascending());
        var resultado = search.isBlank()
                ? repository.findAll(pageable)
                : repository.findByCodigoContainingIgnoreCase(search, pageable);
        return resultado.map(sd -> new SectorDepositoDTO(sd.getId(), sd.getCodigo()));
    }
}
