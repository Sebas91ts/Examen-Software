package com.systembpm.system.modules.notification.application.service;

import com.systembpm.system.modules.notification.application.dto.NotificationResponseDto;
import com.systembpm.system.modules.notification.application.dto.UnreadCountResponseDto;
import com.systembpm.system.modules.notification.domain.Notification;
import com.systembpm.system.modules.notification.infrastructure.repository.NotificationRepository;
import com.systembpm.system.modules.client.domain.ClientProcessInstance;
import com.systembpm.system.modules.client.infrastructure.repository.ClientProcessInstanceRepository;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements INotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final String TYPE_TASK_AVAILABLE = "TASK_AVAILABLE";
    private static final String TYPE_TASK_CLAIMED = "TASK_CLAIMED";
    private static final String TYPE_TASK_COMPLETED = "TASK_COMPLETED";
    private static final String TYPE_AI_ANALYSIS = "AI_ANALYSIS";
    private static final String TYPE_PROCESS_STARTED = "PROCESS_STARTED";
    private static final String TYPE_PROCESS_UPDATED = "PROCESS_UPDATED";

    private final NotificationRepository notificationRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClientProcessInstanceRepository clientProcessInstanceRepository;
    private final IRealtimeEventService realtimeEventService;
    private final IPushNotificationService pushNotificationService;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UsuarioRepository usuarioRepository,
            ClientProcessInstanceRepository clientProcessInstanceRepository,
            IRealtimeEventService realtimeEventService,
            IPushNotificationService pushNotificationService) {
        this.notificationRepository = notificationRepository;
        this.usuarioRepository = usuarioRepository;
        this.clientProcessInstanceRepository = clientProcessInstanceRepository;
        this.realtimeEventService = realtimeEventService;
        this.pushNotificationService = pushNotificationService;
    }

    @Override
    public void notifyTaskAvailableForArea(
            String areaId,
            String areaNombre,
            String processInstanceId,
            String taskId,
            String taskName) {
        if (areaId == null || areaId.isBlank()) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findByAreaIdAndActivoTrue(areaId.trim());
        String normalizedTaskName = safeValue(taskName, "Nueva tarea");
        String normalizedArea = safeValue(areaNombre, "tu area");

        for (Usuario usuario : usuarios) {
            createNotification(
                    usuario,
                    "Nueva tarea disponible",
                    "La tarea \"" + normalizedTaskName + "\" esta disponible para el area " + normalizedArea + ".",
                    TYPE_TASK_AVAILABLE,
                    processInstanceId,
                    taskId);
        }
    }

    @Override
    public void notifyTaskClaimed(Map<String, Object> taskSnapshot, String claimedBy) {
        notifyAreaUsers(
                taskSnapshot,
                "Tarea tomada",
                "La tarea \"" + resolveTaskName(taskSnapshot) + "\" fue tomada por " + safeValue(claimedBy, "un usuario") + ".",
                TYPE_TASK_CLAIMED);
    }

    @Override
    public void notifyTaskCompleted(Map<String, Object> taskSnapshot, String completedBy) {
        log.info("notifyTaskCompleted invocado para taskId={} completedBy={}",
                stringValue(taskSnapshot != null ? taskSnapshot.get("id") : null),
                safeValue(completedBy, "desconocido"));
        notifyAreaUsers(
                taskSnapshot,
                "Tarea completada",
                "La tarea \"" + resolveTaskName(taskSnapshot) + "\" fue completada por " + safeValue(completedBy, "un usuario") + ".",
                TYPE_TASK_COMPLETED);
        notifyClientOwnerProgress(taskSnapshot, completedBy);
    }

    @Override
    public void notifyUserByEmail(String userEmail, String title, String message, String type, String processInstanceId, String taskId) {
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }

        Usuario usuario = usuarioRepository.findByEmail(userEmail.trim()).orElse(null);
        if (usuario == null) {
            return;
        }

        createNotification(usuario, title, message, type, processInstanceId, taskId);
    }

    public void notifyAiAnalysisForAdmins(
            String processName,
            String processId,
            String analysisId,
            String message) {
        List<Usuario> admins = usuarioRepository.findByRolesInAndActivoTrue(List.of("ROLE_ADMIN", "ROLE_BPM_MANAGER"));
        String normalizedProcessName = safeValue(processName, "Proceso sin nombre");
        String normalizedMessage = safeValue(
                message,
                "IA detecto una recomendacion en el proceso \"" + normalizedProcessName + "\".");

        for (Usuario usuario : admins) {
            createNotification(
                    usuario,
                    "Recomendacion IA",
                    normalizedMessage,
                    TYPE_AI_ANALYSIS,
                    processId,
                    analysisId);
        }
    }

    @Override
    public List<NotificationResponseDto> listarMisNotificaciones(String userEmail) {
        validateUserEmail(userEmail);
        return notificationRepository.findByUserEmailOrderByCreatedAtDesc(userEmail.trim()).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public UnreadCountResponseDto obtenerCantidadNoLeidas(String userEmail) {
        validateUserEmail(userEmail);
        return UnreadCountResponseDto.builder()
                .unreadCount(notificationRepository.countByUserEmailAndReadFalse(userEmail.trim()))
                .build();
    }

    @Override
    public NotificationResponseDto marcarComoLeida(String notificationId, String userEmail) {
        if (notificationId == null || notificationId.isBlank()) {
            throw new IllegalArgumentException("El id de la notificacion es obligatorio");
        }
        validateUserEmail(userEmail);

        Notification notification = notificationRepository.findByIdAndUserEmail(notificationId.trim(), userEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("La notificacion no existe o no pertenece al usuario"));

        notification.setRead(true);
        return toDto(notificationRepository.save(notification));
    }

    @Override
    public void marcarTodasComoLeidas(String userEmail) {
        validateUserEmail(userEmail);
        List<Notification> notifications = notificationRepository.findByUserEmailOrderByCreatedAtDesc(userEmail.trim());
        notifications.forEach(notification -> notification.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    private void notifyAreaUsers(Map<String, Object> taskSnapshot, String title, String message, String type) {
        if (taskSnapshot == null || taskSnapshot.isEmpty()) {
            return;
        }

        String areaId = stringValue(taskSnapshot.get("areaId"));
        if (areaId == null || areaId.isBlank()) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findByAreaIdAndActivoTrue(areaId.trim());
        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskId = stringValue(taskSnapshot.get("id"));

        for (Usuario usuario : usuarios) {
            createNotification(usuario, title, message, type, processInstanceId, taskId);
        }
    }

    private void notifyClientOwnerProgress(Map<String, Object> taskSnapshot, String completedBy) {
        if (taskSnapshot == null || taskSnapshot.isEmpty()) {
            log.debug("notifyClientOwnerProgress omitido: taskSnapshot vacio");
            return;
        }

        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        if (processInstanceId == null || processInstanceId.isBlank()) {
            log.debug("notifyClientOwnerProgress omitido: processInstanceId ausente para taskId={}",
                    stringValue(taskSnapshot.get("id")));
            return;
        }

        ClientProcessInstance clientInstance = clientProcessInstanceRepository.findByProcessInstanceId(processInstanceId.trim())
                .orElse(null);
        if (clientInstance == null || clientInstance.getClientEmail() == null || clientInstance.getClientEmail().isBlank()) {
            log.debug("notifyClientOwnerProgress omitido: no existe cliente asociado a processInstanceId={}", processInstanceId.trim());
            return;
        }

        if (completedBy != null && !completedBy.isBlank()
                && completedBy.trim().equalsIgnoreCase(clientInstance.getClientEmail().trim())) {
            log.debug("notifyClientOwnerProgress omitido: completedBy coincide con el cliente owner processInstanceId={} clientEmail={}",
                    processInstanceId.trim(), clientInstance.getClientEmail().trim());
            return;
        }

        String processName = safeValue(stringValue(taskSnapshot.get("nombreProceso")), "Tu tramite");
        String taskName = resolveTaskName(taskSnapshot);
        log.info("notifyClientOwnerProgress enviando a clientEmail={} processInstanceId={} taskId={}",
                clientInstance.getClientEmail().trim(),
                processInstanceId.trim(),
                stringValue(taskSnapshot.get("id")));
        String message = "Se completo la tarea \"" + taskName + "\" en \"" + processName + "\".";
        createNotificationByEmail(
                clientInstance.getClientEmail(),
                "Tu tramite avanza",
                message,
                TYPE_PROCESS_UPDATED,
                processInstanceId.trim(),
                stringValue(taskSnapshot.get("id")));
    }

    private void createNotification(
            Usuario usuario,
            String title,
            String message,
            String type,
            String processInstanceId,
            String taskId) {
        if (usuario == null || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(usuario.getId())
                .userEmail(usuario.getEmail().trim())
                .title(title)
                .message(message)
                .type(type)
                .read(false)
                .createdAt(LocalDateTime.now())
                .relatedProcessInstanceId(processInstanceId)
                .relatedTaskId(taskId)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        log.info("Notificacion creada para usuario={} tipo={}", savedNotification.getUserEmail(), savedNotification.getType());
        if (pushNotificationService == null) {
            log.warn("Push no enviado porque el servicio de push no esta disponible para usuario={}", savedNotification.getUserEmail());
        }
        realtimeEventService.publishNotificationEvent(
                savedNotification.getUserId(),
                savedNotification.getUserEmail(),
                savedNotification.getTitle(),
                savedNotification.getMessage(),
                savedNotification.getRelatedProcessInstanceId(),
                savedNotification.getRelatedTaskId());
        if (pushNotificationService != null) {
            log.info("Intentando enviar push a usuario={} titulo={} processInstanceId={} taskId={}",
                    usuario.getEmail(),
                    savedNotification.getTitle(),
                    savedNotification.getRelatedProcessInstanceId(),
                    savedNotification.getRelatedTaskId());
            pushNotificationService.sendToUser(
                    usuario,
                    savedNotification.getTitle(),
                    savedNotification.getMessage(),
                    buildPushData(savedNotification));
        }
    }

    private void createNotificationByEmail(
            String userEmail,
            String title,
            String message,
            String type,
            String processInstanceId,
            String taskId) {
        if (userEmail == null || userEmail.isBlank()) {
            return;
        }

        Usuario usuario = usuarioRepository.findByEmail(userEmail.trim()).orElse(null);
        if (usuario == null) {
            return;
        }

        createNotification(usuario, title, message, type, processInstanceId, taskId);
    }

    private NotificationResponseDto toDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .userEmail(notification.getUserEmail())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .read(notification.getRead())
                .createdAt(notification.getCreatedAt())
                .relatedProcessInstanceId(notification.getRelatedProcessInstanceId())
                .relatedTaskId(notification.getRelatedTaskId())
                .build();
    }

    private void validateUserEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }
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

        value = stringValue(taskSnapshot.get("taskDefinitionKey"));
        return safeValue(value, "Tarea sin nombre");
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Map<String, String> buildPushData(Notification notification) {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        putIfText(data, "title", notification.getTitle());
        putIfText(data, "message", notification.getMessage());
        putIfText(data, "type", notification.getType());
        putIfText(data, "processInstanceId", notification.getRelatedProcessInstanceId());
        putIfText(data, "taskId", notification.getRelatedTaskId());
        putIfText(data, "notificationId", notification.getId());
        return data;
    }

    private void putIfText(Map<String, String> data, String key, String value) {
        if (data == null || key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }

        data.put(key.trim(), value.trim());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
