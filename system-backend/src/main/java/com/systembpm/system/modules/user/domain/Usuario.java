package com.systembpm.system.modules.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entidad principal de Usuario para el sistema BPM.
 * Implementa UserDetails para integración nativa con Spring Security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class Usuario implements UserDetails {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String nombre;

    private String apellido;

    private String password;

    private String areaId;

    private String areaNombre;

    // Roles del usuario (ej: ROLE_ADMIN, ROLE_USER, ROLE_BPM_MANAGER)
    private List<String> roles;

    private List<String> pushTokens;

    private Boolean activo;

    @CreatedDate
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.roles == null) {
            return List.of();
        }
        return this.roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.activo != null && this.activo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.activo != null && this.activo;
    }
}
