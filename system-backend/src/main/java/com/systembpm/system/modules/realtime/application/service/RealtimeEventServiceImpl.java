package com.systembpm.system.modules.realtime.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeEventServiceImpl implements IRealtimeEventService {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishNotificationEvent(
            String userId,
            String userEmail,
            String title,
            String message,
            String processInstanceId,
            String taskId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        Map<String, Object> event = baseEvent("NOTIFICATION", title, message, processInstanceId, taskId, null, userId, Map.of(
                "userEmail", userEmail));
        messagingTemplate.convertAndSend("/topic/notifications/" + userId.trim(), (Object) event);
    }

    @Override
    public void publishTaskAvailableForArea(String areaId, String processInstanceId, String taskId, String taskName) {
        if (areaId == null || areaId.isBlank()) {
            return;
        }

        Map<String, Object> event = baseEvent(
                "TASK_AVAILABLE",
                "Nueva tarea disponible",
                "La tarea \"" + safeValue(taskName, "Nueva tarea") + "\" esta disponible para tu area.",
                processInstanceId,
                taskId,
                areaId,
                null,
                Map.of());

        messagingTemplate.convertAndSend("/topic/areas/" + areaId.trim() + "/tasks", (Object) event);
        messagingTemplate.convertAndSend("/topic/tasks", (Object) event);
        publishProcessUpdated(processInstanceId, stringValue(event.get("message")), mapPayload(taskId, areaId, taskName, null));
    }

    @Override
    public void publishTaskCreated(Map<String, Object> taskSnapshot) {
        if (taskSnapshot == null || taskSnapshot.isEmpty()) {
            return;
        }

        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskId = stringValue(taskSnapshot.get("id"));
        String areaId = stringValue(taskSnapshot.get("areaId"));
        String areaNombre = stringValue(taskSnapshot.get("areaNombre"));
        String taskName = resolveTaskName(taskSnapshot);

        Map<String, Object> event = baseEvent(
                "TASK_CREATED",
                "Nueva tarea creada",
                "La tarea \"" + taskName + "\" fue creada para el area " + safeValue(areaNombre, "asignada") + ".",
                processInstanceId,
                taskId,
                areaId,
                null,
                mapPayload(taskId, areaId, taskName, null));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        if (payload != null) {
            payload.put("areaNombre", areaNombre);
            payload.put("processInstanceId", processInstanceId);
        }

        sendTaskEvents(areaId, processInstanceId, event);
    }

    @Override
    public void publishTaskClaimed(Map<String, Object> taskSnapshot, String claimedBy) {
        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskId = stringValue(taskSnapshot.get("id"));
        String areaId = stringValue(taskSnapshot.get("areaId"));
        String taskName = resolveTaskName(taskSnapshot);

        Map<String, Object> event = baseEvent(
                "TASK_CLAIMED",
                "Tarea tomada",
                "La tarea \"" + taskName + "\" fue tomada por " + safeValue(claimedBy, "un usuario") + ".",
                processInstanceId,
                taskId,
                areaId,
                null,
                mapPayload(taskId, areaId, taskName, claimedBy));

        sendTaskEvents(areaId, processInstanceId, event);
    }

    @Override
    public void publishTaskCompleted(Map<String, Object> taskSnapshot, String completedBy) {
        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskId = stringValue(taskSnapshot.get("id"));
        String areaId = stringValue(taskSnapshot.get("areaId"));
        String taskName = resolveTaskName(taskSnapshot);

        Map<String, Object> event = baseEvent(
                "TASK_COMPLETED",
                "Tarea completada",
                "La tarea \"" + taskName + "\" fue completada por " + safeValue(completedBy, "un usuario") + ".",
                processInstanceId,
                taskId,
                areaId,
                null,
                mapPayload(taskId, areaId, taskName, completedBy));

        sendTaskEvents(areaId, processInstanceId, event);
    }

    @Override
    public void publishProcessUpdated(String processInstanceId, String message, Map<String, Object> payload) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }

        Map<String, Object> event = baseEvent(
                "PROCESS_UPDATED",
                "Proceso actualizado",
                safeValue(message, "La instancia de proceso fue actualizada."),
                processInstanceId,
                stringValue(payload != null ? payload.get("taskId") : null),
                stringValue(payload != null ? payload.get("areaId") : null),
                null,
                payload != null ? payload : Map.of());

        messagingTemplate.convertAndSend("/topic/process-instances/" + processInstanceId.trim(), (Object) event);
    }

    private void sendTaskEvents(String areaId, String processInstanceId, Map<String, Object> event) {
        if (areaId != null && !areaId.isBlank()) {
            messagingTemplate.convertAndSend("/topic/areas/" + areaId.trim() + "/tasks", (Object) event);
        }
        messagingTemplate.convertAndSend("/topic/tasks", (Object) event);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        publishProcessUpdated(processInstanceId, stringValue(event.get("message")), payload);
    }

    private Map<String, Object> baseEvent(
            String type,
            String title,
            String message,
            String processInstanceId,
            String taskId,
            String areaId,
            String userId,
            Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("title", title);
        event.put("message", message);
        event.put("createdAt", LocalDateTime.now());
        event.put("relatedProcessInstanceId", processInstanceId);
        event.put("relatedTaskId", taskId);
        event.put("areaId", areaId);
        event.put("userId", userId);
        event.put("payload", payload);
        return event;
    }

    private Map<String, Object> mapPayload(String taskId, String areaId, String taskName, String actor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("areaId", areaId);
        payload.put("taskName", taskName);
        if (actor != null && !actor.isBlank()) {
            payload.put("actor", actor);
        }
        return payload;
    }

    private String resolveTaskName(Map<String, Object> taskSnapshot) {
        String value = stringValue(taskSnapshot.get("name"));
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        value = stringValue(taskSnapshot.get("nombreTarea"));
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return safeValue(stringValue(taskSnapshot.get("taskDefinitionKey")), "Tarea sin nombre");
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
