package com.systembpm.system.modules.security.infrastructure.dto;

import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la respuesta de autenticación.
 * Contiene el token JWT y los datos del usuario autenticado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String token;

    private String tokenType;

    private Long expiresIn;

    private UsuarioResponseDto usuario;
}
