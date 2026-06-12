package com.systembpm.system.modules.user.application.service;

import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.dto.UsuarioUpdateDto;
import com.systembpm.system.modules.user.application.mapper.UsuarioMapper;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Implementacion del servicio de usuarios.
 * Contiene la logica de negocio para la gestion de usuarios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements IUsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final AreaRepository areaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UsuarioResponseDto registrarUsuario(UsuarioCreateDto dto) {
        log.info("Registrando nuevo usuario con email: {}", dto.getEmail());

        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            log.warn("Intento de registro con email duplicado: {}", dto.getEmail());
            throw new IllegalArgumentException("El email ya esta registrado en el sistema");
        }

        Usuario usuario = usuarioMapper.toEntity(dto);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuario.setRoles(List.of("ROLE_CLIENT"));
        asignarAreaSiCorresponde(usuario, areaClientePorDefecto(dto.getAreaId()));

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        log.info("Usuario registrado exitosamente con ID: {}", usuarioGuardado.getId());

        return usuarioMapper.toResponseDto(usuarioGuardado);
    }

    @Override
    public UsuarioResponseDto crearUsuarioComoAdmin(UsuarioCreateDto dto) {
        log.info("Creando usuario desde admin con email: {}", dto.getEmail());

        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            log.warn("Intento de alta administrativa con email duplicado: {}", dto.getEmail());
            throw new IllegalArgumentException("El email ya esta registrado en el sistema");
        }

        Usuario usuario = usuarioMapper.toEntity(dto);
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        asignarAreaSiCorresponde(usuario, dto.getAreaId());
        usuario.setRoles(sanitizedAdminRoles(dto.getRoles()));

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        log.info("Usuario creado por admin exitosamente con ID: {}", usuarioGuardado.getId());

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
    public Optional<UsuarioResponseDto> actualizarUsuario(String id, UsuarioUpdateDto dto) {
        log.info("Actualizando usuario con ID: {}", id);

        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id);
        if (usuarioOpt.isEmpty()) {
            log.warn("Intento de actualizar usuario inexistente con ID: {}", id);
            return Optional.empty();
        }

        Usuario usuario = usuarioOpt.get();

        if (!usuario.getEmail().equals(dto.getEmail()) && usuarioRepository.existsByEmail(dto.getEmail())) {
            log.warn("Intento de actualizar con email duplicado: {}", dto.getEmail());
            throw new IllegalArgumentException("El email ya esta registrado en el sistema");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setApellido(dto.getApellido());
        usuario.setEmail(dto.getEmail());
        asignarAreaSiCorresponde(usuario, dto.getAreaId());
        aplicarRolesPermitidos(usuario, dto.getRoles(), true);

        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) {
                throw new IllegalArgumentException("La contrasena debe tener al menos 6 caracteres");
            }
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

    private void asignarAreaSiCorresponde(Usuario usuario, String areaId) {
        if (areaId == null || areaId.isBlank()) {
            usuario.setAreaId(null);
            usuario.setAreaNombre(null);
            usuario.setTenantId(null);
            return;
        }

        String normalizedAreaId = areaId.trim();
        Area area = areaRepository.findById(normalizedAreaId)
                .orElseThrow(() -> new IllegalArgumentException("El area seleccionada no existe"));

        usuario.setAreaId(area.getId());
        usuario.setAreaNombre(area.getNombre());
        usuario.setTenantId(area.getId());
    }

    private String areaClientePorDefecto(String requestedAreaId) {
        if (requestedAreaId != null && !requestedAreaId.isBlank()) {
            return requestedAreaId.trim();
        }

        return areaRepository.findByNombreIgnoreCase("Cliente")
                .filter(area -> !Boolean.FALSE.equals(area.getActiva()))
                .map(Area::getId)
                .orElse(null);
    }

    private void aplicarRolesPermitidos(Usuario usuario, List<String> requestedRoles, boolean allowRoleChanges) {
        List<String> sanitizedRoles = sanitizeRoles(requestedRoles);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!allowRoleChanges) {
            if (isAdmin) {
                usuario.setRoles(sanitizedRoles.isEmpty() ? List.of("ROLE_USER") : sanitizedRoles);
            } else {
                usuario.setRoles(List.of("ROLE_USER"));
            }
            return;
        }

        if (sanitizedRoles.isEmpty()) {
            return;
        }

        usuario.setRoles(isAdmin ? sanitizedRoles : List.of("ROLE_USER"));
    }

    private List<String> sanitizedAdminRoles(List<String> requestedRoles) {
        return List.of("ROLE_USER");
    }

    private List<String> sanitizeRoles(List<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            return List.of();
        }

        List<String> allowedRoles = List.of("ROLE_USER", "ROLE_CLIENT", "ROLE_BPM_MANAGER", "ROLE_ADMIN");
        return requestedRoles.stream()
                .filter(role -> role != null && allowedRoles.contains(role.trim()))
                .map(String::trim)
                .distinct()
                .toList();
    }
}
