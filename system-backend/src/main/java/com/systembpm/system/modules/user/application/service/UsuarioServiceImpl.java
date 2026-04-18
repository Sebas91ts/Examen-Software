package com.systembpm.system.modules.user.application.service;

import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.mapper.UsuarioMapper;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Implementación del servicio de usuarios.
 * Contiene la lógica de negocio para la gestión de usuarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements IUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UsuarioResponseDto registrarUsuario(UsuarioCreateDto dto) {
        log.info("Registrando nuevo usuario con email: {}", dto.getEmail());

        // Validar que el email no exista
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            log.warn("Intento de registro con email duplicado: {}", dto.getEmail());
            throw new IllegalArgumentException("El email ya está registrado en el sistema");
        }

        // Convertir DTO a Entity
        Usuario usuario = usuarioMapper.toEntity(dto);

        // Encriptar la contraseña
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));

        // Guardar en la base de datos
        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        log.info("Usuario registrado exitosamente con ID: {}", usuarioGuardado.getId());

        return usuarioMapper.toResponseDto(usuarioGuardado);
    }

    @Override
    public List<UsuarioResponseDto> listarUsuarios() {
        log.info("Listando todos los usuarios del sistema");
        List<Usuario> usuarios = usuarioRepository.findAll();
        return usuarioMapper.toResponseDtoList(usuarios);
    }

    @Override
    public Optional<UsuarioResponseDto> obtenerUsuarioPorId(String id) {
        log.info("Buscando usuario por ID: {}", id);
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
        return usuarioOpt.map(usuarioMapper::toResponseDto);
    }

    @Override
    public Optional<UsuarioResponseDto> actualizarUsuario(String id, UsuarioCreateDto dto) {
        log.info("Actualizando usuario con ID: {}", id);

        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
        if (usuarioOpt.isEmpty()) {
            log.warn("Intento de actualizar usuario inexistente con ID: {}", id);
            return Optional.empty();
        }

        Usuario usuario = usuarioOpt.get();

        // Validar que el nuevo email no esté duplicado (si cambió el email)
        if (!usuario.getEmail().equals(dto.getEmail()) && usuarioRepository.existsByEmail(dto.getEmail())) {
            log.warn("Intento de actualizar con email duplicado: {}", dto.getEmail());
            throw new IllegalArgumentException("El email ya está registrado en el sistema");
        }

        // Actualizar campos
        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setEmail(dto.getEmail());

        // Actualizar contraseña solo si se proporciona una nueva
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        Usuario usuarioActualizado = usuarioRepository.save(usuario);
        log.info("Usuario actualizado exitosamente con ID: {}", usuarioActualizado.getId());

        return Optional.of(usuarioMapper.toResponseDto(usuarioActualizado));
    }

    @Override
    public boolean eliminarUsuario(String id) {
        log.info("Eliminando usuario con ID: {}", id);

        if (!usuarioRepository.existsById(id)) {
            log.warn("Intento de eliminar usuario inexistente con ID: {}", id);
            return false;
        }

        // Soft delete: desactivar el usuario en lugar de eliminarlo físicamente
        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();
            usuario.setActivo(false);
            usuarioRepository.save(usuario);
            log.info("Usuario desactivado exitosamente con ID: {}", id);
            return true;
        }

        return false;
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }
}
