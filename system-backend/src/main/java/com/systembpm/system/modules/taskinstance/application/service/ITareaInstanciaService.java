package com.systembpm.system.modules.taskinstance.application.service;

import com.systembpm.system.modules.processinstance.domain.ProcesoInstancia;
import com.systembpm.system.modules.taskinstance.application.dto.TareaInstanciaResponseDto;

import java.util.List;

public interface ITareaInstanciaService {

    TareaInstanciaResponseDto crearPrimeraTareaDesdeInstancia(ProcesoInstancia instancia, String xmlProceso);

    List<TareaInstanciaResponseDto> listar();

    List<TareaInstanciaResponseDto> listarPorInstancia(String processInstanceId);

    TareaInstanciaResponseDto obtenerPorId(String id);
}
