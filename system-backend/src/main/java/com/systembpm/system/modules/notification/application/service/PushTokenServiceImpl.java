package com.systembpm.system.modules.notification.application.service;

import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenServiceImpl implements IPushTokenService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public void registrarToken(String userEmail, String token) {
        Usuario usuario = obtenerUsuario(userEmail);
        if (usuario == null || token == null || token.isBlank()) {
            return;
        }

        List<String> pushTokens = normalizarTokens(usuario.getPushTokens());
        pushTokens.add(token.trim());
        usuario.setPushTokens(new ArrayList<>(pushTokens));
        usuarioRepository.save(usuario);
        log.info("Token push registrado para usuario={} totalTokens={}", usuario.getEmail(), pushTokens.size());
    }

    @Override
    public void eliminarToken(String userEmail, String token) {
        Usuario usuario = obtenerUsuario(userEmail);
        if (usuario == null || token == null || token.isBlank()) {
            return;
        }

        List<String> pushTokens = normalizarTokens(usuario.getPushTokens());
        if (pushTokens.removeIf(existing -> existing.equals(token.trim()))) {
            usuario.setPushTokens(new ArrayList<>(pushTokens));
            usuarioRepository.save(usuario);
            log.info("Token push eliminado para usuario={} totalTokens={}", usuario.getEmail(), pushTokens.size());
        }
    }

    private Usuario obtenerUsuario(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return null;
        }

        return usuarioRepository.findByEmail(userEmail.trim()).orElse(null);
    }

    private List<String> normalizarTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> normalizados = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                normalizados.add(token.trim());
            }
        }
        return new ArrayList<>(normalizados);
    }
}
