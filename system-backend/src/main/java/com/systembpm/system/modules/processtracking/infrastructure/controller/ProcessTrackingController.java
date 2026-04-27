package com.systembpm.system.modules.processtracking.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.client.infrastructure.repository.ClientProcessInstanceRepository;
import com.systembpm.system.modules.processtracking.application.dto.ProcessInstanceTrackingResponseDto;
import com.systembpm.system.modules.processtracking.application.service.IProcessTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/process-instances")
@RequiredArgsConstructor
public class ProcessTrackingController {

    private final IProcessTrackingService processTrackingService;
    private final ClientProcessInstanceRepository clientProcessInstanceRepository;

    @GetMapping("/{processInstanceId}/tracking")
    public ResponseEntity<ApiResponse<ProcessInstanceTrackingResponseDto>> obtenerTracking(
            @PathVariable String processInstanceId) {
        log.info("Solicitud GET /api/process-instances/{}/tracking", processInstanceId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && hasRole(authentication, "ROLE_CLIENT")) {
            String clientEmail = authentication.getName();
            boolean ownsInstance = clientProcessInstanceRepository
                    .findByProcessInstanceIdAndClientEmail(processInstanceId, clientEmail)
                    .isPresent();

            if (!ownsInstance) {
                return ResponseEntity.status(403).body(ApiResponse.error("No tienes acceso a esta instancia"));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Tracking de la instancia obtenido exitosamente",
                processTrackingService.obtenerTracking(processInstanceId)));
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || role == null || role.isBlank()) {
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equalsIgnoreCase(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }
}
