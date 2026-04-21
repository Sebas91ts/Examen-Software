package com.systembpm.system.modules.camunda.application.service;

import java.util.List;
import java.util.Map;

public interface CamundaService {
    Map<String, Object> desplegarProceso(String procesoId);

    Map<String, Object> iniciarInstancia(String processKey);

    List<Map<String, Object>> listarTareas();

    Map<String, Object> obtenerTarea(String taskId);

    Map<String, Object> completarTarea(String taskId);
}
