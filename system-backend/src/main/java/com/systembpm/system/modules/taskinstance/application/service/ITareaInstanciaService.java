package com.systembpm.system.modules.taskinstance.application.service;

import com.systembpm.system.modules.taskinstance.application.dto.TareaInstanciaResponseDto;

import java.util.List;

public interface ITareaInstanciaService {

    TareaInstanciaResponseDto crearPrimeraTareaDesdeInstancia(String processInstanceId, String processDefinitionId, String nombreProceso, String xmlProceso);

    List<TareaInstanciaResponseDto> listar();

    List<TareaInstanciaResponseDto> listarPendientes();

    List<TareaInstanciaResponseDto> listarPorInstancia(String processInstanceId);

    List<TareaInstanciaResponseDto> listarPorArea(String areaId);

    List<TareaInstanciaResponseDto> listarPorUsuario(String assignedTo);

    List<TareaInstanciaResponseDto> listarPorProceso(String nombreProceso);

    TareaInstanciaResponseDto obtenerPorId(String id);
}
