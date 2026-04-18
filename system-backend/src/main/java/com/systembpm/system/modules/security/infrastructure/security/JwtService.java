package com.systembpm.system.modules.security.infrastructure.security;

import com.systembpm.system.modules.user.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio para generar y validar tokens JWT.
 * Maneja la creación, validación y extracción de información de los tokens.
 */
@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    /**
     * Genera un token JWT para un usuario.
     *
     * @param usuario El usuario autenticado
     * @return El token JWT generado
     */
    public String generarToken(Usuario usuario) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", usuario.getRoles() == null ? List.of() : List.copyOf(usuario.getRoles()));

        return crearToken(claims, usuario.getEmail());
    }

    /**
     * Extrae el email (username) de un token JWT.
     *
     * @param token El token JWT
     * @return El email del usuario
     */
    public String obtenerEmailDelToken(String token) {
        return extraerClaim(token, Claims::getSubject);
    }

    /**
     * Extrae la fecha de expiración de un token JWT.
     *
     * @param token El token JWT
     * @return La fecha de expiración
     */
    public Date obtenerFechaExpiracionDelToken(String token) {
        return extraerClaim(token, Claims::getExpiration);
    }

    /**
     * Valida si un token JWT es válido.
     * Verifica que no haya expirado y que el email coincida con el subject del token.
     *
     * @param token El token JWT a validar
     * @param email  El email esperado
     * @return true si el token es válido, false en caso contrario
     */
    public boolean esTokenValido(String token, String email) {
        final String emailDelToken = obtenerEmailDelToken(token);
        return emailDelToken != null
                && emailDelToken.equals(email)
                && !esTokenExpirado(token);
    }

    /**
     * Crea un token JWT con claims adicionales.
     *
     * @param extraClaims Claims adicionales (ej: roles, permisos)
     * @param email       El email del usuario
     * @return El token JWT generado
     */
    private String crearToken(Map<String, Object> extraClaims, String email) {
        SecretKey key = getSigningKey();

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(key)
                .compact();
    }

    /**
     * Verifica si un token ha expirado.
     *
     * @param token El token JWT
     * @return true si el token ha expirado, false en caso contrario
     */
    private boolean esTokenExpirado(String token) {
        return obtenerFechaExpiracionDelToken(token).before(new Date());
    }

    /**
     * Extrae un claim específico de un token JWT.
     *
     * @param token          El token JWT
     * @param claimsResolver Función para extraer el claim
     * @return El valor del claim
     */
    private <T> T extraerClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extraerTodosLosClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrae todos los claims de un token JWT.
     *
     * @param token El token JWT
     * @return Los claims del token
     */
    private Claims extraerTodosLosClaims(String token) {
        SecretKey key = getSigningKey();
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Obtiene la clave de firma para validar los tokens JWT.
     *
     * @return La clave de firma
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Obtiene los roles asociados a un token JWT.
     *
     * @param token El token JWT
     * @return Una lista de roles
     */
    public List<String> obtenerRolesDelToken(String token) {
        Claims claims = extraerTodosLosClaims(token);
        Object rolesClaim = claims.get("roles");

        if (!(rolesClaim instanceof List<?> roles)) {
            return Collections.emptyList();
        }

        return roles.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }
}
