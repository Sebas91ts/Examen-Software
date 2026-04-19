package com.systembpm.system.modules.security.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.security.application.service.AuthService;
import com.systembpm.system.modules.security.infrastructure.dto.AuthResponseDto;
import com.systembpm.system.modules.security.infrastructure.dto.LoginRequestDto;
import com.systembpm.system.modules.security.infrastructure.security.JwtService;
import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.service.IUsuarioService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final JwtService jwtService;

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
            @Valid @RequestBody LoginRequestDto loginRequest,
            HttpServletResponse response) {
        log.info("Solicitud POST /api/auth/login para email: {}", loginRequest.getEmail());
        
        AuthResponseDto authResponse = authService.login(loginRequest);

        ResponseCookie jwtCookie = jwtService.crearCookieJwt(authResponse.getToken());
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());
        
        return ResponseEntity.ok(
                ApiResponse.success("Autenticación exitosa", authResponse)
        );
    }

    /**
     * Devuelve el usuario autenticado a partir del contexto de seguridad.
     *
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> obtenerUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No hay una sesión autenticada"));
        }

        UsuarioResponseDto usuario = authService.obtenerUsuarioAutenticado(authentication.getName());

        return ResponseEntity.ok(
                ApiResponse.success("Usuario autenticado recuperado correctamente", usuario)
        );
    }

    /**
     * Cierra la sesión del usuario eliminando la cookie JWT.
     *
     * DELETE /api/auth/logout
     */
    @DeleteMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> cerrarSesion(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, jwtService.limpiarCookieJwt().toString());

        return ResponseEntity.ok(
                ApiResponse.success("Sesión cerrada correctamente", null)
        );
    }
}
