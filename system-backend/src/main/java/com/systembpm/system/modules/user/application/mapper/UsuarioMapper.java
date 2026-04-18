package com.systembpm.system.modules.user.application.mapper;

import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.domain.Usuario;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper para convertir entre la entidad Usuario y sus DTOs.
 * Centraliza la lógica de transformación de datos.
 */
@Component
public class UsuarioMapper {

    /**
     * Convierte un DTO de creación a una entidad Usuario.
     * La contraseña aún no está encriptada (eso lo hace el Service).
     */
    public Usuario toEntity(UsuarioCreateDto dto) {
        return Usuario.builder()
                .nombre(dto.getNombre())
                .apellido(dto.getApellido())
                .email(dto.getEmail())
                .password(dto.getPassword()) // Se encriptará en el Service
                .roles(List.of("ROLE_USER")) // Rol por defecto
                .activo(true)
                .build();
    }

    /**
     * Convierte una entidad Usuario a un DTO de respuesta.
     * NO incluye la contraseña por seguridad.
     */
    public UsuarioResponseDto toResponseDto(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return UsuarioResponseDto.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .email(usuario.getEmail())
                .roles(usuario.getRoles())
                .activo(usuario.getActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .fechaActualizacion(usuario.getFechaActualizacion())
                .build();
    }

    /**
     * Convierte una lista de Usuarios a una lista de DTOs de respuesta.
     */
    public List<UsuarioResponseDto> toResponseDtoList(List<Usuario> usuarios) {
        return usuarios.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }
}
