package com.systembpm.system.modules.notification.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.notification.application.service.IPushTokenService;
import com.systembpm.system.modules.notification.infrastructure.dto.PushTokenRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/notifications/me/push-token")
@RequiredArgsConstructor
public class PushTokenController {

    private final IPushTokenService pushTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> registrarToken(@Valid @RequestBody PushTokenRequestDto request) {
        String userEmail = getAuthenticatedEmail();
        validarCliente();
        log.info("Solicitud POST /api/notifications/me/push-token para {}", userEmail);
        pushTokenService.registrarToken(userEmail, request != null ? request.getToken() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Token push registrado correctamente", null));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> eliminarToken(@RequestParam("token") String token) {
        String userEmail = getAuthenticatedEmail();
        validarCliente();
        log.info("Solicitud DELETE /api/notifications/me/push-token para {}", userEmail);
        pushTokenService.eliminarToken(userEmail, token);
        return ResponseEntity.ok(ApiResponse.success("Token push eliminado correctamente", null));
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("No hay una sesion autenticada");
        }
        return authentication.getName();
    }

    private void validarCliente() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new IllegalArgumentException("No tienes permisos para registrar notificaciones push");
        }

        boolean isClient = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CLIENT".equalsIgnoreCase(authority.getAuthority()));
        if (!isClient) {
            throw new IllegalArgumentException("No tienes permisos para registrar notificaciones push");
        }
    }
}
