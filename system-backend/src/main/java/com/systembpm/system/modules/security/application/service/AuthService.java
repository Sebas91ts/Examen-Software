package com.systembpm.system.modules.security.application.service;

import com.systembpm.system.modules.security.infrastructure.dto.AuthResponseDto;
import com.systembpm.system.modules.security.infrastructure.dto.LoginRequestDto;
import com.systembpm.system.modules.security.infrastructure.security.JwtService;
import com.systembpm.system.modules.user.application.mapper.UsuarioMapper;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación.
 * Maneja el login y generación de tokens JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UsuarioMapper usuarioMapper;

    /**
     * Autentica un usuario y genera un token JWT.
     * 
     * @param loginRequest Credenciales del usuario
     * @return Respuesta con token JWT y datos del usuario
     */
    public AuthResponseDto login(LoginRequestDto loginRequest) {
        log.info("Intento de autenticación para email: {}", loginRequest.getEmail());

        // Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> {
                    log.warn("Email no encontrado: {}", loginRequest.getEmail());
                    return new BadCredentialsException("Email o contraseña incorrectos");
                });

        // Validar que el usuario esté activo
        if (!usuario.getActivo()) {
            log.warn("Intento de login con usuario inactivo: {}", usuario.getEmail());
            throw new BadCredentialsException("El usuario está desactivado. Contacte al administrador.");
        }

        // Validar contraseña
        if (!passwordEncoder.matches(loginRequest.getPassword(), usuario.getPassword())) {
            log.warn("Contraseña incorrecta para email: {}", loginRequest.getEmail());
            throw new BadCredentialsException("Email o contraseña incorrectos");
        }

        // Generar token JWT
        String token = jwtService.generarToken(usuario);

        log.info("Autenticación exitosa para usuario: {}", usuario.getEmail());

        // Construir respuesta
        return AuthResponseDto.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(86400000L) // 24 horas
                .usuario(usuarioMapper.toResponseDto(usuario))
                .build();
    }
}
