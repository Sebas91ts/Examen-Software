package com.systembpm.system.modules.user.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.dto.UsuarioUpdateDto;
import com.systembpm.system.modules.user.application.service.IUsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de usuarios (CRUD).
 * Los endpoints de autenticación (registro/login) están en AuthController.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UsuarioController {

    private final IUsuarioService usuarioService;

    /**
     * Crea un usuario desde el panel administrativo.
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> crearUsuario(
            @Valid @RequestBody UsuarioCreateDto dto) {
        log.info("Solicitud POST /api/users para email: {}", dto.getEmail());
        UsuarioResponseDto usuarioCreado = usuarioService.crearUsuarioComoAdmin(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Usuario creado exitosamente", usuarioCreado));
    }

    /**
     * Lista todos los usuarios del sistema.
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponseDto>>> listarUsuarios() {
        log.info("Solicitud GET /api/users");
        List<UsuarioResponseDto> usuarios = usuarioService.listarUsuarios();
        return ResponseEntity.ok(
                ApiResponse.success("Usuarios listados exitosamente", usuarios)
        );
    }

    /**
     * Obtiene un usuario por su ID.
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> obtenerUsuarioPorId(
            @PathVariable String id) {
        log.info("Solicitud GET /api/users/{}", id);
        return usuarioService.obtenerUsuarioPorId(id)
                .map(usuario -> ResponseEntity.ok(
                        ApiResponse.success("Usuario encontrado", usuario)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Usuario no encontrado")));
    }

    /**
     * Actualiza los datos de un usuario existente.
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponseDto>> actualizarUsuario(
            @PathVariable String id,
            @Valid @RequestBody UsuarioUpdateDto dto) {
        log.info("Solicitud PUT /api/users/{}", id);
        return usuarioService.actualizarUsuario(id, dto)
                .map(usuario -> ResponseEntity.ok(
                        ApiResponse.success("Usuario actualizado exitosamente", usuario)
                ))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Usuario no encontrado")));
    }

    /**
     * Elimina un usuario del sistema (soft delete).
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable String id) {
        log.info("Solicitud DELETE /api/users/{}", id);
        boolean eliminado = usuarioService.eliminarUsuario(id);
        
        if (eliminado) {
            return ResponseEntity.ok(
                    ApiResponse.success("Usuario eliminado exitosamente", null)
            );
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado"));
        }
    }
}
