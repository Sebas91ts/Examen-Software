package com.systembpm.system.modules.processinstance.application.service;

import com.systembpm.system.modules.processinstance.application.dto.ProcesoInstanciaResponseDto;

import java.util.List;

public interface IProcesoInstanciaService {

    ProcesoInstanciaResponseDto iniciarDesdeDefinicion(String processDefinitionId);

    List<ProcesoInstanciaResponseDto> listar();

    ProcesoInstanciaResponseDto obtenerPorId(String id);
}
