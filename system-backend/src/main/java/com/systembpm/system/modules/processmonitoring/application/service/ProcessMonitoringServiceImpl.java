package com.systembpm.system.modules.processmonitoring.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.processmonitoring.application.dto.ProcessActivityStatDto;
import com.systembpm.system.modules.processmonitoring.application.dto.ProcessMonitorInstanceDto;
import com.systembpm.system.modules.processmonitoring.application.dto.ProcessMonitoringResponseDto;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMonitoringServiceImpl implements IProcessMonitoringService {

    private final ProcesoRepository procesoRepository;
    private final CamundaService camundaService;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TareaInstanciaRepository tareaInstanciaRepository;

    @Override
    public ProcessMonitoringResponseDto obtenerMonitoreo(String processId) {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException("El processId es obligatorio");
        }

        try {
            Proceso proceso = procesoRepository.findById(processId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("El proceso solicitado no existe"));

            String processKey = proceso.getProcessKey();
            List<Map<String, Object>> activeTasks = camundaService.listarTareasTodas().stream()
                    .filter(task -> matchesProcess(task, processKey))
                    .filter(task -> hasText(stringValue(task.get("processInstanceId"))))
                    .toList();

            Map<String, List<Map<String, Object>>> tasksByInstance = activeTasks.stream()
                    .collect(Collectors.groupingBy(
                            task -> stringValue(task.get("processInstanceId")).trim(),
                            LinkedHashMap::new,
                            Collectors.toList()));

            List<TaskExecutionLog> logs = taskExecutionLogRepository.findAll();
            List<TareaInstancia> localTasks = tareaInstanciaRepository.findAll();

            List<ProcessActivityStatDto> stats = activeTasks.stream()
                    .filter(task -> hasText(stringValue(task.get("taskDefinitionKey"))))
                    .collect(Collectors.groupingBy(
                            task -> stringValue(task.get("taskDefinitionKey")).trim(),
                            LinkedHashMap::new,
                            Collectors.toList()))
                    .entrySet().stream()
                    .map(entry -> ProcessActivityStatDto.builder()
                            .taskDefinitionKey(entry.getKey())
                            .taskName(resolveTaskName(entry.getValue().get(0)))
                            .activeInstances(entry.getValue().stream()
                                    .map(task -> stringValue(task.get("processInstanceId")))
                                    .filter(this::hasText)
                                    .map(String::trim)
                                    .distinct()
                                    .count())
                            .build())
                    .sorted(Comparator.comparing(ProcessActivityStatDto::getActiveInstances).reversed()
                            .thenComparing(item -> item.getTaskName() != null ? item.getTaskName() : ""))
                    .toList();

            List<ProcessMonitorInstanceDto> instances = tasksByInstance.entrySet().stream()
                    .map(entry -> mapInstance(entry.getKey(), entry.getValue(), logs, localTasks))
                    .sorted(Comparator.comparing(ProcessMonitorInstanceDto::getStartedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            return ProcessMonitoringResponseDto.builder()
                    .processId(proceso.getId())
                    .processKey(processoOrFallback(proceso.getProcessKey(), proceso.getNombre()))
                    .processVersion(proceso.getVersion())
                    .nombreProceso(proceso.getNombre())
                    .estado(proceso.getEstado())
                    .xmlBpmn(proceso.getXml())
                    .totalActiveInstances(instances.size())
                    .activityStats(stats)
                    .instances(instances)
                    .build();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error construyendo monitoreo para processId={}", processId, ex);
            throw new IllegalStateException("No se pudo construir el monitoreo del proceso", ex);
        }
    }

    private ProcessMonitorInstanceDto mapInstance(
            String processInstanceId,
            List<Map<String, Object>> tasks,
            List<TaskExecutionLog> logs,
            List<TareaInstancia> localTasks) {
        return ProcessMonitorInstanceDto.builder()
                .processInstanceId(processInstanceId)
                .businessKey(firstNonBlank(tasks.stream().map(task -> stringValue(task.get("businessKey"))).toList()))
                .startedAt(resolveStartedAt(processInstanceId, logs, localTasks))
                .currentTaskNames(distinctValues(tasks.stream().map(this::resolveTaskName).toList()))
                .currentAreas(distinctValues(tasks.stream().map(task -> stringValue(task.get("areaNombre"))).toList()))
                .assignedUsers(distinctValues(tasks.stream()
                        .map(task -> firstNonBlankValue(
                                stringValue(task.get("assignee")),
                                stringValue(task.get("assignedTo"))))
                        .toList()))
                .activeTaskCount(tasks.size())
                .build();
    }

    private boolean matchesProcess(Map<String, Object> task, String processKey) {
        String enrichedProcessKey = stringValue(task.get("processKey"));
        if (hasText(enrichedProcessKey)) {
            return Objects.equals(enrichedProcessKey.trim(), processKey);
        }

        String taskProcessDefinitionId = stringValue(task.get("processDefinitionId"));
        if (taskProcessDefinitionId == null || taskProcessDefinitionId.isBlank()) {
            return false;
        }

        String taskProcessKey = extractProcessKey(taskProcessDefinitionId);
        return Objects.equals(taskProcessKey, processKey);
    }

    private LocalDateTime resolveStartedAt(
            String processInstanceId,
            List<TaskExecutionLog> logs,
            List<TareaInstancia> localTasks) {
        LocalDateTime fromLocalTask = localTasks.stream()
                .filter(item -> processInstanceId.equals(item.getProcessInstanceId()))
                .map(TareaInstancia::getCreatedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        if (fromLocalTask != null) {
            return fromLocalTask;
        }

        return logs.stream()
                .filter(item -> processInstanceId.equals(item.getProcessInstanceId()))
                .map(item -> item.getCreatedAt() != null ? item.getCreatedAt() : item.getCompletedAt())
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
    }

    private List<String> distinctValues(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String resolveTaskName(Map<String, Object> task) {
        return firstNonBlankValue(
                stringValue(task.get("name")),
                stringValue(task.get("nombreTarea")),
                stringValue(task.get("taskDefinitionKey")),
                "Tarea sin nombre");
    }

    private String extractProcessKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }

        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0].trim() : processDefinitionId.trim();
    }

    private Integer extractProcessVersion(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }

        String[] parts = processDefinitionId.split(":");
        if (parts.length < 2) {
            return null;
        }

        try {
            return Integer.valueOf(parts[1].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String processoOrFallback(String processKey, String nombre) {
        return processKey != null && !processKey.isBlank() ? processKey.trim() : nombre;
    }

    private String firstNonBlank(List<String> values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String firstNonBlankValue(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
