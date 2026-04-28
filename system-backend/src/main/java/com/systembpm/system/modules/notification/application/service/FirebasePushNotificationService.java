package com.systembpm.system.modules.notification.application.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.systembpm.system.modules.user.domain.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FirebasePushNotificationService implements IPushNotificationService {

    private final boolean enabled;
    private final String serviceAccountBase64;
    private final String projectId;
    private volatile boolean initialized;

    public FirebasePushNotificationService(
            @Value("${app.push.firebase.enabled:false}") boolean enabled,
            @Value("${app.push.firebase.service-account-base64:}") String serviceAccountBase64,
            @Value("${app.push.firebase.project-id:}") String projectId) {
        this.enabled = enabled || parseBoolean(resolveEnvValue("FIREBASE_ENABLED"), false);
        this.serviceAccountBase64 = firstNonBlank(
                serviceAccountBase64,
                resolveEnvValue("FIREBASE_SERVICE_ACCOUNT_BASE64"));
        this.projectId = firstNonBlank(
                projectId,
                resolveEnvValue("FIREBASE_PROJECT_ID"));
    }

    @Override
    public void sendToUser(Usuario usuario, String title, String message, Map<String, String> data) {
        if (!enabled || usuario == null) {
            log.debug("Push omitido: enabled={} usuarioNull={}", enabled, usuario == null);
            return;
        }

        try {
            ensureInitialized();
            if (!initialized) {
                log.warn("Push omitido: Firebase no quedo inicializado para usuario={}", usuario.getEmail());
                return;
            }

            List<String> tokens = normalizeTokens(usuario.getPushTokens());
            if (tokens.isEmpty()) {
                log.warn("Push omitido: usuario={} no tiene tokens registrados", usuario.getEmail());
                return;
            }

            Map<String, String> safeData = new LinkedHashMap<>();
            if (data != null) {
                data.forEach((key, value) -> {
                    if (key != null && value != null && !key.isBlank() && !value.isBlank()) {
                        safeData.put(key.trim(), value.trim());
                    }
                });
            }

            int sentCount = 0;
            for (String token : tokens) {
                try {
                    Message.Builder builder = Message.builder()
                            .setToken(token)
                            .setNotification(Notification.builder()
                                    .setTitle(safeText(title, "Sistema BPM"))
                                    .setBody(safeText(message, "Tienes una nueva notificacion"))
                                    .build());

                    if (!safeData.isEmpty()) {
                        builder.putAllData(safeData);
                    }

                    FirebaseMessaging.getInstance().send(builder.build());
                    sentCount++;
                } catch (Exception tokenEx) {
                    log.warn("No se pudo enviar push al token de usuario={}", usuario.getEmail(), tokenEx);
                }
            }

            log.info("Push enviado a usuario={} tokens={} enviados={} title={}",
                    usuario.getEmail(), tokens.size(), sentCount, safeText(title, "Sistema BPM"));
        } catch (Throwable ex) {
            log.warn("No se pudo enviar push a usuario={}", usuario != null ? usuario.getEmail() : null, ex);
        }
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        if (!enabled) {
            return;
        }

        if (serviceAccountBase64 == null || serviceAccountBase64.isBlank()) {
            log.info("Firebase push deshabilitado por falta de credenciales");
            return;
        }

        try {
            byte[] serviceAccountBytes = Base64.getDecoder().decode(serviceAccountBase64.trim());
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountBytes));

            FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(credentials);

            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId.trim());
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(builder.build());
            }

            initialized = true;
            log.info("Firebase push inicializado correctamente");
        } catch (Throwable ex) {
            initialized = false;
            log.warn("No se pudo inicializar Firebase push", ex);
        }
    }

    private List<String> normalizeTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String token : tokens) {
            if (token != null && !token.isBlank()) {
                String trimmed = token.trim();
                if (!normalized.contains(trimmed)) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return "";
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private String resolveEnvValue(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }

        List<Path> candidateFiles = List.of(
                Path.of(".env"),
                Path.of("system-backend/.env"),
                Path.of("../system-backend/.env"));

        for (Path file : candidateFiles) {
            String value = readValueFromEnvFile(file, key.trim());
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String readValueFromEnvFile(Path file, String key) {
        if (file == null || key == null || key.isBlank() || !Files.exists(file)) {
            return "";
        }

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String candidateKey = trimmed.substring(0, separatorIndex).trim();
                if (!candidateKey.equals(key)) {
                    continue;
                }

                return trimmed.substring(separatorIndex + 1).trim();
            }
        } catch (IOException ex) {
            log.debug("No se pudo leer el archivo {} para la variable {}", file, key, ex);
        }

        return "";
    }
}
