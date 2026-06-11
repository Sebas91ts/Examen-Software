package com.systembpm.system.modules.camunda.application.service;

import java.util.List;
import java.util.Map;

public interface CamundaService {
    Map<String, Object> desplegarProceso(String procesoId);

    Map<String, Object> iniciarInstancia(String processKey);

    Map<String, Object> iniciarInstanciaPorDefinicion(String processKey, String businessKey);

    Map<String, Object> iniciarInstanciaConVariables(String processKey, Map<String, Object> variables);

    List<Map<String, Object>> listarInstanciasProcesoActivas();

    Map<String, Object> obtenerInstanciaProceso(String processInstanceId);

    List<Map<String, Object>> listarTareas();

    List<Map<String, Object>> listarTareasPorAssignee(String assignee);

    List<Map<String, Object>> listarTareasPorArea(String areaId);

    List<Map<String, Object>> listarTareasTodas();

    Map<String, Object> obtenerTarea(String taskId);

    Map<String, Object> obtenerVariablesTarea(String taskId);

    Map<String, Object> completarTarea(String taskId);

    Map<String, Object> completarTarea(String taskId, Map<String, Object> variables);

    Map<String, Object> completarTarea(String taskId, Map<String, Object> variables, String completedBy);

    Map<String, Object> tomarTarea(String taskId, String userEmail);
}
