package com.systembpm.system.modules.taskexecutionlog.application.service;

import com.systembpm.system.modules.taskexecutionlog.application.dto.TaskExecutionLogResponseDto;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionLogServiceImpl implements ITaskExecutionLogService {

    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TareaInstanciaRepository tareaInstanciaRepository;

    @Override
    public void registrarEjecucion(Map<String, Object> taskSnapshot, Map<String, Object> formData, String completedBy) {
        if (taskSnapshot == null || taskSnapshot.isEmpty()) {
            throw new IllegalArgumentException("La informacion de la tarea es obligatoria");
        }
        if (completedBy == null || completedBy.isBlank()) {
            throw new IllegalArgumentException("El usuario que completa la tarea es obligatorio");
        }

        LocalDateTime completedAt = LocalDateTime.now();
        TaskExecutionLog logEntry = TaskExecutionLog.builder()
                .processInstanceId(stringValue(taskSnapshot.get("processInstanceId")))
                .processDefinitionId(stringValue(taskSnapshot.get("processDefinitionId")))
                .processKey(resolveProcessKey(taskSnapshot))
                .processVersion(resolveProcessVersion(taskSnapshot))
                .taskDefinitionKey(stringValue(taskSnapshot.get("taskDefinitionKey")))
                .taskName(resolveTaskName(taskSnapshot))
                .areaId(stringValue(taskSnapshot.get("areaId")))
                .areaNombre(stringValue(taskSnapshot.get("areaNombre")))
                .assignedTo(resolveAssignedTo(taskSnapshot))
                .completedBy(completedBy.trim())
                .formData(copyMap(formData))
                .createdAt(resolveCreatedAt(taskSnapshot))
                .completedAt(completedAt)
                .build();

        taskExecutionLogRepository.save(logEntry);
        log.info("Historial de ejecucion registrado para processInstanceId={} taskDefinitionKey={}",
                logEntry.getProcessInstanceId(), logEntry.getTaskDefinitionKey());
    }

    @Override
    public List<TaskExecutionLogResponseDto> listarPorInstancia(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio");
        }

        return taskExecutionLogRepository.findByProcessInstanceIdOrderByCompletedAtAscCreatedAtAsc(processInstanceId.trim())
                .stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    private TaskExecutionLogResponseDto mapToResponseDto(TaskExecutionLog item) {
        return TaskExecutionLogResponseDto.builder()
                .id(item.getId())
                .processInstanceId(item.getProcessInstanceId())
                .processDefinitionId(item.getProcessDefinitionId())
                .processKey(item.getProcessKey())
                .processVersion(item.getProcessVersion())
                .taskDefinitionKey(item.getTaskDefinitionKey())
                .taskName(item.getTaskName())
                .areaId(item.getAreaId())
                .areaNombre(item.getAreaNombre())
                .assignedTo(item.getAssignedTo())
                .completedBy(item.getCompletedBy())
                .formData(item.getFormData())
                .createdAt(item.getCreatedAt())
                .completedAt(item.getCompletedAt())
                .build();
    }

    private String resolveProcessKey(Map<String, Object> taskSnapshot) {
        String processKey = stringValue(taskSnapshot.get("processKey"));
        if (processKey != null && !processKey.isBlank()) {
            return processKey.trim();
        }

        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }

        String[] parts = processDefinitionId.split(":");
        return parts.length > 0 ? parts[0].trim() : processDefinitionId.trim();
    }

    private Integer resolveProcessVersion(Map<String, Object> taskSnapshot) {
        Object rawVersion = taskSnapshot.get("processVersion");
        if (rawVersion instanceof Number number) {
            return number.intValue();
        }

        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
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

    private String resolveTaskName(Map<String, Object> taskSnapshot) {
        String taskName = stringValue(taskSnapshot.get("name"));
        if (taskName != null && !taskName.isBlank()) {
            return taskName.trim();
        }

        taskName = stringValue(taskSnapshot.get("nombreTarea"));
        if (taskName != null && !taskName.isBlank()) {
            return taskName.trim();
        }

        return stringValue(taskSnapshot.get("taskDefinitionKey"));
    }

    private String resolveAssignedTo(Map<String, Object> taskSnapshot) {
        String assignedTo = stringValue(taskSnapshot.get("assignee"));
        if (assignedTo != null && !assignedTo.isBlank()) {
            return assignedTo.trim();
        }

        assignedTo = stringValue(taskSnapshot.get("assignedTo"));
        return assignedTo != null ? assignedTo.trim() : null;
    }

    private LocalDateTime resolveCreatedAt(Map<String, Object> taskSnapshot) {
        LocalDateTime localTaskCreatedAt = resolveCreatedAtFromLocalTask(taskSnapshot);
        if (localTaskCreatedAt != null) {
            return localTaskCreatedAt;
        }

        String created = stringValue(taskSnapshot.get("created"));
        if (created == null || created.isBlank()) {
            created = stringValue(taskSnapshot.get("createdAt"));
        }

        if (created == null || created.isBlank()) {
            return null;
        }

        try {
            return OffsetDateTime.parse(created).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(created.replace("Z", ""));
            } catch (DateTimeParseException nestedEx) {
                try {
                    return OffsetDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                            .toLocalDateTime();
                } catch (DateTimeParseException patternEx) {
                    try {
                        return OffsetDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"))
                                .toLocalDateTime();
                    } catch (DateTimeParseException finalEx) {
                        log.debug("No se pudo parsear createdAt de la tarea: {}", created, finalEx);
                        return null;
                    }
                }
            }
        }
    }

    private LocalDateTime resolveCreatedAtFromLocalTask(Map<String, Object> taskSnapshot) {
        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskDefinitionKey = stringValue(taskSnapshot.get("taskDefinitionKey"));

        if (processInstanceId == null || processInstanceId.isBlank()
                || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            return null;
        }

        return tareaInstanciaRepository
                .findTopByProcessInstanceIdAndTaskDefinitionKeyOrderByCreatedAtDesc(
                        processInstanceId.trim(),
                        taskDefinitionKey.trim())
                .map(tarea -> tarea.getCreatedAt())
                .orElse(null);
    }

    private Map<String, Object> copyMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(source);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
