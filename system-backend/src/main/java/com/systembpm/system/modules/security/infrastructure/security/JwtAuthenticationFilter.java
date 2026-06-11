package com.systembpm.system.modules.security.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filtro que se ejecuta en cada petición HTTP para validar el token JWT.
 * Si el token es válido, autentica al usuario en el contexto de seguridad.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_COOKIE_NAME = "auth_token";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/logout")
                || path.startsWith("/api/onlyoffice/")
                || path.startsWith("/camunda")
                || path.startsWith("/engine-rest")
                || path.startsWith("/app")
                || path.startsWith("/lib")
                || path.startsWith("/api/admin")
                || path.equals("/ws")
                || path.startsWith("/ws/")
                || path.contains("/camunda/assets")
                || path.contains("/camunda/app")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            final String token = extraerToken(request);

            if (token == null || token.isBlank()) {
                log.debug("No se encontró token JWT en la petición");
                filterChain.doFilter(request, response);
                return;
            }
            final String email = jwtService.obtenerEmailDelToken(token);
            
            log.debug("Token JWT procesado para email: {}", email);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (!jwtService.esTokenValido(token, email)) {
                    log.warn("Token JWT inválido para email: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }

                List<SimpleGrantedAuthority> authorities = jwtService.obtenerRolesDelToken(token).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("Usuario autenticado por JWT: {}", email);
            }
        } catch (Exception e) {
            log.error("Error al procesar token JWT: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extraerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        if (request.getCookies() == null) {
            return null;
        }

        for (var cookie : request.getCookies()) {
            if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
