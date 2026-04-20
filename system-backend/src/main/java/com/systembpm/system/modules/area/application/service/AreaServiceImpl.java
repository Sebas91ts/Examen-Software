package com.systembpm.system.modules.area.application.service;

import com.systembpm.system.modules.area.application.dto.AreaCreateDto;
import com.systembpm.system.modules.area.application.dto.AreaResponseDto;
import com.systembpm.system.modules.area.application.dto.AreaUpdateDto;
import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AreaServiceImpl implements IAreaService {

    private final AreaRepository areaRepository;

    @Override
    public AreaResponseDto crear(AreaCreateDto dto) {
        String nombre = normalizar(dto.getNombre());
        log.info("Creando area: {}", nombre);

        if (areaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalArgumentException("Ya existe un area con ese nombre");
        }

        LocalDateTime now = LocalDateTime.now();
        Area area = Area.builder()
                .nombre(nombre)
                .descripcion(dto.getDescripcion())
                .activa(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(areaRepository.save(area));
    }

    @Override
    public List<AreaResponseDto> listarTodas() {
        log.info("Listando todas las areas");
        return areaRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<AreaResponseDto> listarActivas() {
        log.info("Listando areas activas");
        return areaRepository.findByActivaTrue().stream().map(this::toResponse).toList();
    }

    @Override
    public Optional<AreaResponseDto> obtenerPorId(String id) {
        log.info("Buscando area por id: {}", id);
        return areaRepository.findById(id).map(this::toResponse);
    }

    @Override
    public Optional<AreaResponseDto> actualizar(String id, AreaUpdateDto dto) {
        log.info("Actualizando area: {}", id);

        Optional<Area> areaOpt = areaRepository.findById(id);
        if (areaOpt.isEmpty()) {
            return Optional.empty();
        }

        Area area = areaOpt.get();
        String nuevoNombre = normalizar(dto.getNombre());

        if (!area.getNombre().equalsIgnoreCase(nuevoNombre) && areaRepository.existsByNombreIgnoreCase(nuevoNombre)) {
            throw new IllegalArgumentException("Ya existe un area con ese nombre");
        }

        area.setNombre(nuevoNombre);
        area.setDescripcion(dto.getDescripcion());
        if (dto.getActiva() != null) {
            area.setActiva(dto.getActiva());
        }
        area.setUpdatedAt(LocalDateTime.now());

        return Optional.of(toResponse(areaRepository.save(area)));
    }

    @Override
    public boolean desactivar(String id) {
        log.info("Desactivando area: {}", id);

        Optional<Area> areaOpt = areaRepository.findById(id);
        if (areaOpt.isEmpty()) {
            return false;
        }

        Area area = areaOpt.get();
        area.setActiva(false);
        area.setUpdatedAt(LocalDateTime.now());
        areaRepository.save(area);
        return true;
    }

    private AreaResponseDto toResponse(Area area) {
        return AreaResponseDto.builder()
                .id(area.getId())
                .nombre(area.getNombre())
                .descripcion(area.getDescripcion())
                .activa(area.getActiva())
                .createdAt(area.getCreatedAt())
                .updatedAt(area.getUpdatedAt())
                .build();
    }

    private String normalizar(String value) {
        return value == null ? null : value.trim();
    }
}
