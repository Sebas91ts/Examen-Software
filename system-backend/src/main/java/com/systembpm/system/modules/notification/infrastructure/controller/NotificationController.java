package com.systembpm.system.modules.notification.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.notification.application.dto.NotificationResponseDto;
import com.systembpm.system.modules.notification.application.dto.UnreadCountResponseDto;
import com.systembpm.system.modules.notification.application.service.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationServiceImpl notificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> listarMisNotificaciones() {
        String userEmail = getAuthenticatedEmail();
        log.info("Solicitud GET /api/notifications/me para {}", userEmail);
        return ResponseEntity.ok(ApiResponse.success(
                "Notificaciones obtenidas exitosamente",
                notificationService.listarMisNotificaciones(userEmail)));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponseDto>> obtenerNoLeidas() {
        String userEmail = getAuthenticatedEmail();
        log.info("Solicitud GET /api/notifications/me/unread-count para {}", userEmail);
        return ResponseEntity.ok(ApiResponse.success(
                "Cantidad de notificaciones no leidas obtenida exitosamente",
                notificationService.obtenerCantidadNoLeidas(userEmail)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> marcarComoLeida(@PathVariable String id) {
        String userEmail = getAuthenticatedEmail();
        log.info("Solicitud PATCH /api/notifications/{}/read para {}", id, userEmail);
        return ResponseEntity.ok(ApiResponse.success(
                "Notificacion marcada como leida",
                notificationService.marcarComoLeida(id, userEmail)));
    }

    @PatchMapping("/me/read-all")
    public ResponseEntity<ApiResponse<Void>> marcarTodasComoLeidas() {
        String userEmail = getAuthenticatedEmail();
        log.info("Solicitud PATCH /api/notifications/me/read-all para {}", userEmail);
        notificationService.marcarTodasComoLeidas(userEmail);
        return ResponseEntity.ok(ApiResponse.success("Todas las notificaciones fueron marcadas como leidas", null));
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No hay una sesion autenticada");
        }
        return authentication.getName();
    }
}
