package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.processinstance.domain.ProcesoInstancia;
import com.systembpm.system.modules.processinstance.infrastructure.repository.ProcesoInstanciaRepository;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRoutingContextServiceImpl implements AiRoutingContextService {

    private static final int DASHBOARD_LIMIT = 50;

    private final CamundaService camundaService;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TareaInstanciaRepository tareaInstanciaRepository;
    private final ProcesoInstanciaRepository procesoInstanciaRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public Map<String, Object> buildTaskContext(String taskId, String requesterEmail) {
        Map<String, Object> taskSnapshot = camundaService.obtenerTarea(taskId);
        return buildTaskContextFromSnapshot(taskSnapshot, requesterEmail);
    }

    @Override
    public Map<String, Object> buildInstanceContext(String processInstanceId, String requesterEmail) {
        ProcesoInstancia instance = procesoInstanciaRepository.findById(processInstanceId).orElse(null);
        List<TareaInstancia> tasks = tareaInstanciaRepository.findByProcessInstanceIdOrderByCreatedAtAsc(processInstanceId);
        List<DocumentMetadata> documents = documentMetadataRepository.findByProcessInstanceIdOrderByUploadedAtDesc(processInstanceId);

        Map<String, Object> context = baseContext();
        context.put("instance", mapInstance(instance, processInstanceId));
        context.put("documents", documents.stream().map(this::mapDocument).toList());
        context.put("history", taskExecutionLogRepository.findByProcessInstanceIdOrderByCompletedAtAscCreatedAtAsc(processInstanceId)
                .stream().map(this::mapExecutionLog).toList());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("instanceAgeHours", instanceAgeHours(instance));
        metrics.put("activeTasks", tasks.stream().filter(this::isActiveTask).count());
        metrics.put("completedTasks", tasks.stream().filter(task -> task.getCompletedAt() != null).count());
        metrics.put("missingRequiredDocuments", countProblematicDocuments(documents));
        context.put("metrics", metrics);
        log.info("ai.routing.instance-context processInstanceId={} requester={}", processInstanceId, requesterEmail);
        return context;
    }

    @Override
    public Map<String, Object> buildDashboardContext(String requesterEmail) {
        Map<String, Object> context = baseContext();
        Map<String, Object> metrics = new LinkedHashMap<>();

        List<Map<String, Object>> activeTasks = camundaService.listarTareasTodas().stream()
                .limit(DASHBOARD_LIMIT)
                .map(task -> buildTaskContextFromSnapshot(task, requesterEmail))
                .toList();
        List<Map<String, Object>> activeInstances = procesoInstanciaRepository.findAll().stream()
                .filter(instance -> instance.getFinishedAt() == null)
                .sorted(Comparator.comparing(ProcesoInstancia::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(DASHBOARD_LIMIT)
                .map(instance -> buildInstanceContext(instance.getId(), requesterEmail))
                .toList();

        metrics.put("activeTaskContexts", activeTasks);
        metrics.put("activeInstanceContexts", activeInstances);
        context.put("metrics", metrics);
        log.info("ai.routing.dashboard-context requester={} tasks={} instances={}",
                requesterEmail, activeTasks.size(), activeInstances.size());
        return context;
    }

    private Map<String, Object> buildTaskContextFromSnapshot(Map<String, Object> taskSnapshot, String requesterEmail) {
        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskDefinitionKey = stringValue(firstNonNull(taskSnapshot.get("taskDefinitionKey"), taskSnapshot.get("taskDefinitionId")));
        String areaId = stringValue(taskSnapshot.get("areaId"));
        List<TaskExecutionLog> allLogs = taskExecutionLogRepository.findAll();
        List<DocumentMetadata> documents = hasText(processInstanceId)
                ? documentMetadataRepository.findByProcessInstanceIdOrderByUploadedAtDesc(processInstanceId)
                : List.of();

        Map<String, Object> context = baseContext();
        context.put("task", mapTask(taskSnapshot));
        context.put("instance", mapInstance(resolveInstance(processInstanceId), processInstanceId));
        context.put("documents", documents.stream().map(this::mapDocument).toList());
        context.put("history", allLogs.stream()
                .filter(log -> Objects.equals(processInstanceId, log.getProcessInstanceId()))
                .map(this::mapExecutionLog)
                .toList());
        context.put("candidates", buildCandidates(areaId, allLogs));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("taskAgeHours", taskAgeHours(taskSnapshot));
        metrics.put("instanceAgeHours", instanceAgeHours(resolveInstance(processInstanceId)));
        metrics.put("averageCompletionHours", averageCompletionHours(allLogs, taskDefinitionKey, areaId));
        metrics.put("areaBacklog", areaBacklog(areaId));
        metrics.put("missingRequiredDocuments", countProblematicDocuments(documents));
        metrics.put("recentRejections", documents.stream()
                .filter(doc -> DocumentLifecycleState.REJECTED.equals(doc.getDocumentState()))
                .count());
        context.put("metrics", metrics);
        log.info("ai.routing.task-context taskId={} processInstanceId={} requester={}",
                taskSnapshot.get("id"), processInstanceId, requesterEmail);
        return context;
    }

    private List<Map<String, Object>> buildCandidates(String areaId, List<TaskExecutionLog> logs) {
        if (!hasText(areaId)) {
            return List.of();
        }
        List<Usuario> users = usuarioRepository.findByAreaIdAndActivoTrue(areaId);
        List<TareaInstancia> activeByArea = tareaInstanciaRepository.findByAreaIdIgnoreCaseOrderByCreatedAtAsc(areaId);
        return users.stream().map(user -> {
            Map<String, Object> candidate = new LinkedHashMap<>();
            String email = user.getEmail();
            candidate.put("type", "USER");
            candidate.put("id", user.getId());
            candidate.put("email", email);
            candidate.put("name", fullName(user));
            candidate.put("backlog", activeByArea.stream()
                    .filter(task -> email != null && email.equalsIgnoreCase(task.getAssignedTo()))
                    .filter(this::isActiveTask)
                    .count());
            candidate.put("completedTasks", logs.stream()
                    .filter(log -> email != null && email.equalsIgnoreCase(log.getCompletedBy()))
                    .count());
            candidate.put("averageCompletionHours", averageCompletionHoursByUser(logs, email));
            return candidate;
        }).toList();
    }

    private double averageCompletionHours(List<TaskExecutionLog> logs, String taskDefinitionKey, String areaId) {
        List<Double> durations = logs.stream()
                .filter(log -> !hasText(taskDefinitionKey) || Objects.equals(taskDefinitionKey, log.getTaskDefinitionKey()))
                .filter(log -> !hasText(areaId) || Objects.equals(areaId, log.getAreaId()))
                .map(this::durationHours)
                .filter(value -> value > 0)
                .toList();
        return durations.isEmpty() ? 8.0 : durations.stream().mapToDouble(Double::doubleValue).average().orElse(8.0);
    }

    private double averageCompletionHoursByUser(List<TaskExecutionLog> logs, String email) {
        if (!hasText(email)) {
            return 8.0;
        }
        List<Double> durations = logs.stream()
                .filter(log -> email.equalsIgnoreCase(String.valueOf(log.getCompletedBy())))
                .map(this::durationHours)
                .filter(value -> value > 0)
                .toList();
        return durations.isEmpty() ? 8.0 : durations.stream().mapToDouble(Double::doubleValue).average().orElse(8.0);
    }

    private long areaBacklog(String areaId) {
        if (!hasText(areaId)) {
            return 0;
        }
        return tareaInstanciaRepository.findByAreaIdIgnoreCaseOrderByCreatedAtAsc(areaId).stream()
                .filter(this::isActiveTask)
                .count();
    }

    private long countProblematicDocuments(List<DocumentMetadata> documents) {
        return documents.stream()
                .filter(doc -> DocumentLifecycleState.PENDING.equals(doc.getDocumentState())
                        || DocumentLifecycleState.REJECTED.equals(doc.getDocumentState()))
                .count();
    }

    private double taskAgeHours(Map<String, Object> taskSnapshot) {
        LocalDateTime createdAt = parseDateTime(firstNonNull(taskSnapshot.get("created"), taskSnapshot.get("createdAt")));
        return createdAt == null ? 0 : hoursBetween(createdAt, LocalDateTime.now());
    }

    private double instanceAgeHours(ProcesoInstancia instance) {
        if (instance == null || instance.getStartedAt() == null) {
            return 0;
        }
        return hoursBetween(instance.getStartedAt(), instance.getFinishedAt() != null ? instance.getFinishedAt() : LocalDateTime.now());
    }

    private double durationHours(TaskExecutionLog log) {
        if (log.getCreatedAt() == null || log.getCompletedAt() == null) {
            return 0;
        }
        return hoursBetween(log.getCreatedAt(), log.getCompletedAt());
    }

    private ProcesoInstancia resolveInstance(String processInstanceId) {
        if (!hasText(processInstanceId)) {
            return null;
        }
        return procesoInstanciaRepository.findById(processInstanceId).orElse(null);
    }

    private Map<String, Object> baseContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("task", Map.of());
        context.put("instance", Map.of());
        context.put("metrics", Map.of());
        context.put("candidates", List.of());
        context.put("history", List.of());
        context.put("documents", List.of());
        return context;
    }

    private Map<String, Object> mapTask(Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.get("id"));
        result.put("name", firstNonNull(task.get("name"), task.get("nombreTarea")));
        result.put("taskDefinitionKey", firstNonNull(task.get("taskDefinitionKey"), task.get("taskDefinitionId")));
        result.put("processInstanceId", task.get("processInstanceId"));
        result.put("processDefinitionId", task.get("processDefinitionId"));
        result.put("areaId", task.get("areaId"));
        result.put("areaName", task.get("areaNombre"));
        result.put("assignee", firstNonNull(task.get("assignee"), task.get("assignedTo")));
        result.put("createdAt", firstNonNull(task.get("created"), task.get("createdAt")));
        return result;
    }

    private Map<String, Object> mapInstance(ProcesoInstancia instance, String fallbackId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", instance != null ? instance.getId() : fallbackId);
        result.put("processKey", instance != null ? instance.getProcessKey() : null);
        result.put("name", instance != null ? instance.getNombreProceso() : null);
        result.put("status", instance != null ? instance.getEstado() : null);
        result.put("startedAt", instance != null ? instance.getStartedAt() : null);
        result.put("finishedAt", instance != null ? instance.getFinishedAt() : null);
        result.put("startedBy", instance != null ? instance.getIniciadoPor() : null);
        return result;
    }

    private Map<String, Object> mapExecutionLog(TaskExecutionLog item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("processInstanceId", item.getProcessInstanceId());
        result.put("taskDefinitionKey", item.getTaskDefinitionKey());
        result.put("taskName", item.getTaskName());
        result.put("areaId", item.getAreaId());
        result.put("areaName", item.getAreaNombre());
        result.put("assignedTo", item.getAssignedTo());
        result.put("completedBy", item.getCompletedBy());
        result.put("createdAt", item.getCreatedAt());
        result.put("completedAt", item.getCompletedAt());
        result.put("durationHours", durationHours(item));
        return result;
    }

    private Map<String, Object> mapDocument(DocumentMetadata document) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", document.getId());
        result.put("name", document.getOriginalName());
        result.put("state", document.getDocumentState());
        result.put("processInstanceId", document.getProcessInstanceId());
        result.put("taskDefinitionKey", document.getTaskDefinitionKey());
        result.put("requirementId", document.getDocumentRequirementId());
        result.put("requirementName", document.getDocumentRequirementName());
        result.put("updatedAt", document.getUpdatedAt());
        return result;
    }

    private boolean isActiveTask(TareaInstancia task) {
        if (task == null || task.getCompletedAt() != null) {
            return false;
        }
        String estado = task.getEstado();
        return estado == null
                || (!estado.equalsIgnoreCase("COMPLETED")
                && !estado.equalsIgnoreCase("FINALIZADA")
                && !estado.equalsIgnoreCase("COMPLETADA"));
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        String text = String.valueOf(value);
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(text);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private double hoursBetween(LocalDateTime start, LocalDateTime end) {
        return Math.max(0, Duration.between(start, end).toMinutes() / 60.0);
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String fullName(Usuario user) {
        List<String> parts = new ArrayList<>();
        if (hasText(user.getNombre())) {
            parts.add(user.getNombre());
        }
        if (hasText(user.getApellido())) {
            parts.add(user.getApellido());
        }
        return parts.isEmpty() ? user.getEmail() : String.join(" ", parts);
    }
}
