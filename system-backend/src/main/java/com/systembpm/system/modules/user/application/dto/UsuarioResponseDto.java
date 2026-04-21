package com.systembpm.system.modules.user.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para responder datos de un usuario.
 * NO incluye información sensible como la contraseña.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDto {

    private String id;

    private String nombre;

    private String apellido;

    private String email;

    private List<String> roles;

    private Boolean activo;

    private String areaId;

    private String areaNombre;

    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaActualizacion;
}
