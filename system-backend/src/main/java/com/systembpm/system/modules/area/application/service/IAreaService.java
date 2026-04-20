package com.systembpm.system.modules.area.application.service;

import com.systembpm.system.modules.area.application.dto.AreaCreateDto;
import com.systembpm.system.modules.area.application.dto.AreaResponseDto;
import com.systembpm.system.modules.area.application.dto.AreaUpdateDto;

import java.util.List;
import java.util.Optional;

public interface IAreaService {

    AreaResponseDto crear(AreaCreateDto dto);

    List<AreaResponseDto> listarTodas();

    List<AreaResponseDto> listarActivas();

    Optional<AreaResponseDto> obtenerPorId(String id);

    Optional<AreaResponseDto> actualizar(String id, AreaUpdateDto dto);

    boolean desactivar(String id);
}
