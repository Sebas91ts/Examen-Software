package com.systembpm.system.modules.security.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.security.application.service.AuthService;
import com.systembpm.system.modules.security.infrastructure.dto.AuthResponseDto;
import com.systembpm.system.modules.security.infrastructure.dto.LoginRequestDto;
import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.service.IUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para autenticación.
 * Maneja registro de usuarios y login con JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final IUsuarioService usuarioService;

    /**
     * Registra un nuevo usuario en el sistema.
     * 
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> registrarUsuario(
            @Valid @RequestBody UsuarioCreateDto dto) {
        log.info("Solicitud POST /api/auth/register para email: {}", dto.getEmail());
        
        UsuarioResponseDto usuarioCreado = usuarioService.registrarUsuario(dto);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuario registrado exitosamente", usuarioCreado));
    }

    /**
     * Autentica un usuario y retorna un token JWT.
     * 
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> autenticarUsuario(
            @Valid @RequestBody LoginRequestDto loginRequest) {
        log.info("Solicitud POST /api/auth/login para email: {}", loginRequest.getEmail());
        
        AuthResponseDto authResponse = authService.login(loginRequest);
        
        return ResponseEntity.ok(
                ApiResponse.success("Autenticación exitosa", authResponse)
        );
    }
}
