package com.systembpm.system.modules.taskexecutionlog.application.service;

import com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto;

import java.util.List;
import java.util.Map;

public interface ITaskExecutionLogService {

    void registrarEjecucion(Map<String, Object> taskSnapshot, Map<String, Object> formData, String completedBy);

    List<TaskExecutionLogResponseDto> listarPorInstancia(String processInstanceId);
}
