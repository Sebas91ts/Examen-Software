package com.systembpm.system.modules.notification.application.service;

import com.systembpm.system.modules.notification.application.dto.NotificationResponseDto;
import com.systembpm.system.modules.notification.application.dto.UnreadCountResponseDto;
import com.systembpm.system.modules.notification.domain.Notification;
import com.systembpm.system.modules.notification.infrastructure.repository.NotificationRepository;
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

    private final NotificationRepository notificationRepository;
    private final UsuarioRepository usuarioRepository;
    private final IRealtimeEventService realtimeEventService;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            UsuarioRepository usuarioRepository,
            IRealtimeEventService realtimeEventService) {
        this.notificationRepository = notificationRepository;
        this.usuarioRepository = usuarioRepository;
        this.realtimeEventService = realtimeEventService;
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
        notifyAreaUsers(
                taskSnapshot,
                "Tarea completada",
                "La tarea \"" + resolveTaskName(taskSnapshot) + "\" fue completada por " + safeValue(completedBy, "un usuario") + ".",
                TYPE_TASK_COMPLETED);
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

        notificationRepository.save(notification);
        log.info("Notificacion creada para usuario={} tipo={}", notification.getUserEmail(), notification.getType());
        realtimeEventService.publishNotificationEvent(
                notification.getUserId(),
                notification.getUserEmail(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getRelatedProcessInstanceId(),
                notification.getRelatedTaskId());
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

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
