package com.systembpm.system.modules.security.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que se ejecuta en cada petición HTTP para validar el token JWT.
 * Si el token es válido, autentica al usuario en el contexto de seguridad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            final String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.debug("No se encontró token JWT en la petición");
                filterChain.doFilter(request, response);
                return;
            }

            final String token = authHeader.substring(BEARER_PREFIX.length());
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
}
