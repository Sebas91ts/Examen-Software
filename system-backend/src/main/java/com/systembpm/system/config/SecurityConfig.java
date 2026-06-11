package com.systembpm.system.config;

import com.systembpm.system.modules.security.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/onlyoffice/**").permitAll()
                .requestMatchers(
                        "/camunda",
                        "/camunda/**",
                        "/camunda/app/**",
                        "/camunda/assets/**",
                        "/camunda/lib/**",
                        "/engine-rest/**",
                        "/app/**",
                        "/api/admin/**",
                        "/lib/**"
                ).permitAll()
                .requestMatchers("/api/client/**").hasRole("CLIENT")
                .requestMatchers("/api/areas/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/forms/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/forms/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/forms/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/forms/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/dashboard/**").hasRole("ADMIN")
                .requestMatchers("/api/procesos/publicados").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/procesos/publicados/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/procesos/**").hasRole("ADMIN")
                .requestMatchers("/api/camunda/deploy/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/camunda/start/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/camunda/tasks/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/proceso-instancias/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/process-instances/**").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/tarea-instancias/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/files/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/documents/**").hasAnyRole("USER", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/notifications/**").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/assistant").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/fill-form").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/assist").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/recommend-process").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/reports").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/document-analysis").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/context").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/voice").hasAnyRole("USER", "CLIENT", "ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/analyze-process").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/analyses/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/suggestions/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/api/ai/**").hasAnyRole("ADMIN", "BPM_MANAGER")
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOrigins(List.of(
        "http://localhost:4200",
        "http://localhost:3000",
        "http://localhost:59244",
        "https://frontend-system-three-flame.vercel.app",
        "https://systembpm-sebas.duckdns.org"
    ));

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);

    return source;
}

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
                "/camunda/app/**",
                "/camunda/assets/**",
                "/camunda/lib/**"
        );
    }
}
