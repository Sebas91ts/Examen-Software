package com.systembpm.system.modules.notification.application.service;

import com.systembpm.system.modules.notification.application.dto.NotificationResponseDto;
import com.systembpm.system.modules.notification.application.dto.UnreadCountResponseDto;

import java.util.List;
import java.util.Map;

public interface INotificationService {

    void notifyTaskAvailableForArea(String areaId, String areaNombre, String processInstanceId, String taskId, String taskName);

    void notifyTaskClaimed(Map<String, Object> taskSnapshot, String claimedBy);

    void notifyTaskCompleted(Map<String, Object> taskSnapshot, String completedBy);

    List<NotificationResponseDto> listarMisNotificaciones(String userEmail);

    UnreadCountResponseDto obtenerCantidadNoLeidas(String userEmail);

    NotificationResponseDto marcarComoLeida(String notificationId, String userEmail);

    void marcarTodasComoLeidas(String userEmail);
}
