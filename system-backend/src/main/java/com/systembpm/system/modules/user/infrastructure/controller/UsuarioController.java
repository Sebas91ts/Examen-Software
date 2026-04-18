package com.systembpm.system.modules.user.infrastructure.controller;

import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.security.application.service.AuthService;
import com.systembpm.system.modules.security.infrastructure.dto.AuthResponseDto;
import com.systembpm.system.modules.security.infrastructure.dto.LoginRequestDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.service.IUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para la gestión de usuarios.
 * Expone endpoints para registro, login y CRUD de usuarios.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UsuarioController {

    private final IUsuarioService usuarioService;
    private final AuthService authService;

    // ==========================================
    // ENDPOINTS DE AUTENTICACIÓN
    // ==========================================

    /**
     * Registra un nuevo usuario en el sistema.
     * 
     * POST /api/auth/register
     */
    @PostMapping("/auth/register")
    public ResponseEntity<UsuarioResponseDto> registrarUsuario(
            @Valid @RequestBody UsuarioCreateDto dto) {
        log.info("Solicitud POST /api/auth/register para email: {}", dto.getEmail());
        UsuarioResponseDto usuarioCreado = usuarioService.registrarUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioCreado);
    }

    /**
     * Autentica un usuario y retorna un token JWT.
     * 
     * POST /api/auth/login
     * Nota: La generación del token se implementará cuando configuremos JWT
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> autenticarUsuario(
            @Valid @RequestBody LoginRequestDto loginRequest) {
        log.info("Solicitud POST /api/auth/login para email: {}", loginRequest.getEmail());
        
        // Autenticar usuario (valida credenciales)
        AuthResponseDto response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // ENDPOINTS DE GESTIÓN DE USUARIOS (CRUD)
    // ==========================================

    /**
     * Lista todos los usuarios del sistema.
     * 
     * GET /api/users
     */
    @GetMapping("/users")
    public ResponseEntity<List<UsuarioResponseDto>> listarUsuarios() {
        log.info("Solicitud GET /api/users");
        List<UsuarioResponseDto> usuarios = usuarioService.listarUsuarios();
        return ResponseEntity.ok(usuarios);
    }

    /**
     * Obtiene un usuario por su ID.
     * 
     * GET /api/users/{id}
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<UsuarioResponseDto> obtenerUsuarioPorId(
            @PathVariable String id) {
        log.info("Solicitud GET /api/users/{}", id);
        return usuarioService.obtenerUsuarioPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Actualiza los datos de un usuario existente.
     * 
     * PUT /api/users/{id}
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<UsuarioResponseDto> actualizarUsuario(
            @PathVariable String id,
            @Valid @RequestBody UsuarioCreateDto dto) {
        log.info("Solicitud PUT /api/users/{}", id);
        return usuarioService.actualizarUsuario(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Elimina un usuario del sistema (soft delete).
     * 
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable String id) {
        log.info("Solicitud DELETE /api/users/{}", id);
        boolean eliminado = usuarioService.eliminarUsuario(id);
        return eliminado 
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
