package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackRequestDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeCallbackResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditingSessionResponseDto;
import com.systembpm.system.modules.document.application.dto.OnlyOfficeEditorConfigResponseDto;
import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentStatus;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.InvalidDocumentAccessException;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.infrastructure.config.DocumentProperties;
import com.systembpm.system.modules.document.infrastructure.config.OnlyOfficeProperties;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficeIntegrationServiceImpl implements OnlyOfficeIntegrationService {

    private static final int STATUS_EDITING = 1;
    private static final int STATUS_READY_TO_SAVE = 2;
    private static final int STATUS_SAVE_ERROR = 3;
    private static final int STATUS_CLOSED_NO_CHANGES = 4;
    private static final int STATUS_FORCE_SAVE = 6;
    private static final int STATUS_FORCE_SAVE_ERROR = 7;

    private final DocumentMetadataRepository documentMetadataRepository;
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentStoragePort documentStoragePort;
    private final DocumentProperties documentProperties;
    private final OnlyOfficeProperties onlyOfficeProperties;
    private final RestTemplate restTemplate;

    @Override
    public OnlyOfficeEditorConfigResponseDto getEditorConfig(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        TaskDocumentConfig taskConfig = resolveTaskConfig(document);
        boolean editable = canEdit(document, taskConfig);
        Instant now = Instant.now();
        document.setUpdatedAt(now);
        document.setUpdatedBy(requester.getEmail());
        String documentKey = resolveStableDocumentKey(document);

        String fileUrl = buildOnlyOfficeContentUrl(document, documentKey);
        log.info("ONLYOFFICE DOCUMENT URL = {}", fileUrl);

        Map<String, Object> docsApiConfig = buildDocsApiConfig(document, requester, fileUrl, documentKey, editable);
        String token = onlyOfficeProperties.enabled() ? signPayload(docsApiConfig) : null;
        if (token != null) {
            docsApiConfig.put("token", token);
        }

        document.setEditable(editable);
        document.setCollaborativeEditing(resolveCollaborative(document, taskConfig));
        document.setOnlyOfficeDocumentKey(documentKey);
        document.setTemplateDocumentId(document.getTemplateDocumentId() != null ? document.getTemplateDocumentId() : taskConfig != null ? taskConfig.getTemplateDocumentId() : null);
        DocumentMetadata saved = documentMetadataRepository.save(document);

        log.info("onlyoffice.editor-config tenantId={} documentId={} user={} editable={} collaborative={} enabled={}",
                requester.getTenantId(), documentId, requester.getEmail(), editable, saved.getCollaborativeEditing(), onlyOfficeProperties.enabled());

        return OnlyOfficeEditorConfigResponseDto.builder()
                .documentId(saved.getId())
                .documentKey(saved.getOnlyOfficeDocumentKey())
                .fileName(saved.getOriginalName())
                .title(saved.getOriginalName())
                .fileType(resolveFileType(saved.getOriginalName()))
                .mode(editable ? "edit" : "view")
                .editable(editable)
                .collaborativeEditing(Boolean.TRUE.equals(saved.getCollaborativeEditing()))
                .readOnlyAfterComplete(taskConfig != null ? taskConfig.getReadOnlyAfterComplete() : null)
                .requireApproval(taskConfig != null ? taskConfig.getRequireApproval() : null)
                .templateDocumentId(saved.getTemplateDocumentId())
                .currentEditor(saved.getCurrentEditor())
                .editingStartedAt(saved.getEditingStartedAt())
                .onlyOfficeReady(onlyOfficeProperties.enabled())
                .message(onlyOfficeProperties.enabled()
                        ? "Configuracion real DocsAPI.DocEditor generada"
                        : "OnlyOffice esta deshabilitado por configuracion")
                .documentServerUrl(sanitizeBaseUrl(onlyOfficeProperties.documentServerUrl()))
                .callbackUrl(onlyOfficeProperties.callbackUrl())
                .token(null)
                .docsApiConfig(docsApiConfig)
                .build();
    }

    @Override
    public OnlyOfficeEditingSessionResponseDto startEditing(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        TaskDocumentConfig taskConfig = resolveTaskConfig(document);
        if (!canEdit(document, taskConfig)) {
            throw new InvalidDocumentAccessException("El documento no puede abrirse en modo edicion");
        }
        boolean collaborative = resolveCollaborative(document, taskConfig);
        if (!collaborative && hasText(document.getCurrentEditor()) && !requester.getEmail().equalsIgnoreCase(document.getCurrentEditor())) {
            throw new InvalidDocumentAccessException("El documento ya esta siendo editado por otro usuario");
        }

        Instant now = Instant.now();
        document.setEditable(true);
        document.setCollaborativeEditing(collaborative);
        if (!hasText(document.getOnlyOfficeDocumentKey())) {
            document.setOnlyOfficeDocumentKey(resolveStableDocumentKey(document));
        }
        document.setCurrentEditor(collaborative ? document.getCurrentEditor() : requester.getEmail());
        if (document.getEditingStartedAt() == null) {
            document.setEditingStartedAt(now);
        }
        document.setUpdatedAt(now);
        document.setUpdatedBy(requester.getEmail());
        DocumentMetadata saved = documentMetadataRepository.save(document);

        log.info("onlyoffice.start-editing tenantId={} documentId={} user={} collaborative={}",
                requester.getTenantId(), documentId, requester.getEmail(), collaborative);
        return editingResponse(saved, true, "Sesion de edicion OnlyOffice iniciada");
    }

    @Override
    public OnlyOfficeEditingSessionResponseDto finishEditing(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata document = findDocument(documentId, requester.getTenantId());
        if (hasText(document.getCurrentEditor()) && !requester.getEmail().equalsIgnoreCase(document.getCurrentEditor()) && !isAdmin(requester)) {
            throw new InvalidDocumentAccessException("Solo el editor actual o un administrador puede finalizar la edicion");
        }
        document.setCurrentEditor(null);
        document.setEditingStartedAt(null);
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(requester.getEmail());
        DocumentMetadata saved = documentMetadataRepository.save(document);
        log.info("onlyoffice.finish-editing tenantId={} documentId={} user={}", requester.getTenantId(), documentId, requester.getEmail());
        return editingResponse(saved, false, "Sesion de edicion OnlyOffice finalizada");
    }

    @Override
    public OnlyOfficeCallbackResponseDto handleCallback(OnlyOfficeCallbackRequestDto request, String authorizationHeader) {
        if (request == null || !hasText(request.getKey())) {
            return callbackError("Callback invalido");
        }
        log.info("onlyoffice.callback.raw status={} key={} urlPresent={} users={} actions={} tokenPresent={} authHeaderPresent={}",
                request.getStatus(),
                request.getKey(),
                hasText(request.getUrl()),
                request.getUsers(),
                request.getActions(),
                hasText(request.getToken()),
                hasText(authorizationHeader));
        validateCallbackToken(request, authorizationHeader);

        DocumentMetadata document = documentMetadataRepository.findByOnlyOfficeDocumentKey(request.getKey())
                .orElse(null);
        if (document == null) {
            log.warn("onlyoffice.callback.document-not-found documentKey={} status={}", request.getKey(), request.getStatus());
            return callbackError("Documento no encontrado para key");
        }

        Integer status = request.getStatus();
        log.info("onlyoffice.callback tenantId={} documentId={} documentKey={} status={} urlPresent={} users={} actions={}",
                document.getTenantId(), document.getId(), request.getKey(), status, hasText(request.getUrl()), request.getUsers(), request.getActions());

        if (status == null || status == STATUS_EDITING) {
            return callbackOk("Estado recibido sin persistencia");
        }
        if (status == STATUS_CLOSED_NO_CHANGES) {
            clearEditingSession(document, "onlyoffice");
            return callbackOk("Sesion cerrada sin cambios");
        }
        if (status == STATUS_SAVE_ERROR || status == STATUS_FORCE_SAVE_ERROR) {
            log.warn("onlyoffice.callback.save-error tenantId={} documentId={} status={}", document.getTenantId(), document.getId(), status);
            return callbackOk("OnlyOffice reporto error de guardado");
        }
        if (status == STATUS_READY_TO_SAVE || status == STATUS_FORCE_SAVE) {
            persistNewVersion(document, request);
            return callbackOk("Nueva version documental persistida");
        }
        return callbackOk("Estado OnlyOffice no persistente procesado");
    }

    @Override
    public DocumentMetadata validateContentToken(String documentId, String token) {
        if (!hasText(documentId) || !hasText(token)) {
            throw new InvalidDocumentAccessException("Token de contenido OnlyOffice invalido");
        }
        var claims = Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
        String tokenDocumentId = claims.get("documentId", String.class);
        String tokenDocumentKey = claims.get("documentKey", String.class);
        if (!documentId.equals(tokenDocumentId)) {
            throw new InvalidDocumentAccessException("Token de contenido no corresponde al documento");
        }
        DocumentMetadata document = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!hasText(document.getOnlyOfficeDocumentKey()) || !document.getOnlyOfficeDocumentKey().equals(tokenDocumentKey)) {
            throw new InvalidDocumentAccessException("Token de contenido no corresponde a la version documental");
        }
        return document;
    }

    @Override
    public byte[] downloadContent(String documentId, String token) {
        DocumentMetadata document = validateContentToken(documentId, token);
        log.info("onlyoffice.content.download tenantId={} documentId={} documentKey={} size={}",
                document.getTenantId(), document.getId(), document.getOnlyOfficeDocumentKey(), document.getSize());
        return documentStoragePort.download(document.getS3Key());
    }

    private Map<String, Object> buildDocsApiConfig(DocumentMetadata document, Usuario requester, String fileUrl, String documentKey, boolean editable) {
        Map<String, Object> documentConfig = new LinkedHashMap<>();
        documentConfig.put("fileType", resolveFileType(document.getOriginalName()));
        documentConfig.put("key", documentKey);
        documentConfig.put("title", document.getOriginalName());
        documentConfig.put("url", fileUrl);

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", editable);
        permissions.put("download", true);
        permissions.put("print", true);
        documentConfig.put("permissions", permissions);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", requester.getId() != null ? requester.getId() : requester.getEmail());
        user.put("name", requester.getEmail());

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", editable ? "edit" : "view");
        editorConfig.put("lang", "es");
        editorConfig.put("callbackUrl", onlyOfficeProperties.callbackUrl());
        editorConfig.put("user", user);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "desktop");
        config.put("documentType", resolveDocumentType(document.getMimeType(), document.getOriginalName()));
        config.put("document", documentConfig);
        config.put("editorConfig", editorConfig);
        return config;
    }

    private String buildOnlyOfficeContentUrl(DocumentMetadata document, String documentKey) {
        String token = signPayload(Map.of(
                "documentId", document.getId(),
                "documentKey", documentKey,
                "tenantId", document.getTenantId()
        ));
        String callbackUrl = onlyOfficeProperties.callbackUrl();
        String baseUrl = callbackUrl.substring(0, callbackUrl.indexOf("/api/onlyoffice"));
        return baseUrl + "/api/onlyoffice/documents/" + document.getId() + "/content?token=" + token;
    }

    private void persistNewVersion(DocumentMetadata current, OnlyOfficeCallbackRequestDto request) {
        if (!hasText(request.getUrl())) {
            throw new DocumentValidationException("OnlyOffice no envio URL para descargar la version modificada");
        }
        ResponseEntity<byte[]> response = restTemplate.exchange(URI.create(request.getUrl()), HttpMethod.GET, null, byte[].class);
        byte[] content = response.getBody();
        if (content == null || content.length == 0) {
            throw new DocumentValidationException("OnlyOffice devolvio un archivo vacio");
        }

        int nextVersion = current.getVersion() == null ? 1 : current.getVersion() + 1;
        String documentId = new ObjectId().toHexString();
        String extension = resolveExtension(current.getOriginalName());
        String fileName = documentId + "-v" + nextVersion + (extension.isBlank() ? "" : "." + extension);
        String s3Key = current.getTenantId() + "/" + current.getProcessInstanceId() + "/" + documentId + "/" + nextVersion + "/" + fileName;
        documentStoragePort.upload(s3Key, current.getMimeType(), content.length, new ByteArrayInputStream(content));

        Instant now = Instant.now();
        DocumentMetadata next = DocumentMetadata.builder()
                .id(documentId)
                .tenantId(current.getTenantId())
                .processInstanceId(current.getProcessInstanceId())
                .fileName(fileName)
                .originalName(current.getOriginalName())
                .mimeType(current.getMimeType())
                .size((long) content.length)
                .s3Key(s3Key)
                .uploadedBy(current.getUploadedBy())
                .uploadedAt(now)
                .version(nextVersion)
                .status(DocumentStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .lastAccessedAt(null)
                .updatedBy(resolveCallbackEditor(current, request))
                .processKey(current.getProcessKey())
                .processVersion(current.getProcessVersion())
                .taskDefinitionKey(current.getTaskDefinitionKey())
                .taskInstanceId(current.getTaskInstanceId())
                .documentState(current.getDocumentState())
                .locked(current.getLocked())
                .lockedBy(current.getLockedBy())
                .lockedAt(current.getLockedAt())
                .comments(current.getComments())
                .folderId(current.getFolderId())
                .tagIds(current.getTagIds())
                .editable(current.getEditable())
                .collaborativeEditing(current.getCollaborativeEditing())
                .templateDocumentId(current.getTemplateDocumentId())
                .onlyOfficeDocumentKey(buildDocumentKey(current.getTenantId(), documentId, nextVersion))
                .currentEditor(null)
                .editingStartedAt(null)
                .build();
        documentMetadataRepository.save(next);

        clearEditingSession(current, next.getUpdatedBy());
        log.info("onlyoffice.version-created tenantId={} previousDocumentId={} newDocumentId={} version={} size={}",
                current.getTenantId(), current.getId(), next.getId(), next.getVersion(), next.getSize());
    }

    private void clearEditingSession(DocumentMetadata document, String updatedBy) {
        document.setCurrentEditor(null);
        document.setEditingStartedAt(null);
        document.setUpdatedAt(Instant.now());
        document.setUpdatedBy(updatedBy);
        documentMetadataRepository.save(document);
    }

    private boolean canEdit(DocumentMetadata document, TaskDocumentConfig config) {
        if (document.getDocumentState() == DocumentLifecycleState.FINAL || document.getDocumentState() == DocumentLifecycleState.ARCHIVED) {
            return false;
        }
        if (Boolean.TRUE.equals(document.getLocked()) && "workflow".equalsIgnoreCase(nullSafe(document.getLockedBy()))) {
            return false;
        }
        if (config != null && Boolean.TRUE.equals(config.getReadOnlyAfterComplete()) && Boolean.TRUE.equals(document.getLocked())) {
            return false;
        }
        if (config != null) {
            return Boolean.TRUE.equals(config.getAllowEditing()) || Boolean.TRUE.equals(config.getEditable());
        }
        return Boolean.TRUE.equals(document.getEditable());
    }

    private boolean resolveCollaborative(DocumentMetadata document, TaskDocumentConfig config) {
        if (config != null && config.getCollaborativeEditing() != null) {
            return Boolean.TRUE.equals(config.getCollaborativeEditing());
        }
        return Boolean.TRUE.equals(document.getCollaborativeEditing());
    }

    private String ensureFreshDocumentKey(DocumentMetadata document) {
        String key = resolveStableDocumentKey(document);
        document.setOnlyOfficeDocumentKey(key);
        return key;
    }

    private String resolveStableDocumentKey(DocumentMetadata document) {
        if (hasText(document.getOnlyOfficeDocumentKey()) && !isLegacyTimestampDocumentKey(document)) {
            return document.getOnlyOfficeDocumentKey();
        }
        String stableKey = buildDocumentKey(document.getTenantId(), document.getId(), document.getVersion());
        if (hasText(document.getOnlyOfficeDocumentKey())) {
            log.info("onlyoffice.document-key.normalized documentId={} oldKey={} newKey={}",
                    document.getId(), document.getOnlyOfficeDocumentKey(), stableKey);
        }
        return stableKey;
    }

    private String buildDocumentKey(String tenantId, String documentId, Integer version) {
        return sanitizeKey(tenantId + "-" + documentId + "-v" + (version == null ? 1 : version));
    }

    private boolean isLegacyTimestampDocumentKey(DocumentMetadata document) {
        String currentKey = document.getOnlyOfficeDocumentKey();
        if (!hasText(currentKey)) {
            return false;
        }
        String stablePrefix = buildDocumentKey(document.getTenantId(), document.getId(), document.getVersion()) + "-";
        String suffix = currentKey.startsWith(stablePrefix) ? currentKey.substring(stablePrefix.length()) : "";
        return !suffix.isBlank() && suffix.matches("\\d{10,}");
    }

    private void validateCallbackToken(OnlyOfficeCallbackRequestDto request, String authorizationHeader) {
        if (!onlyOfficeProperties.enabled()) {
            return;
        }
        if (!hasText(onlyOfficeProperties.jwtSecret())) {
            throw new InvalidDocumentAccessException("JWT de OnlyOffice no esta configurado");
        }
        String token = hasText(request.getToken()) ? request.getToken() : extractBearer(authorizationHeader);
        if (!hasText(token)) {
            throw new InvalidDocumentAccessException("Callback OnlyOffice sin token");
        }
        Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token.trim());
    }

    private String signPayload(Map<String, Object> payload) {
        if (!hasText(onlyOfficeProperties.jwtSecret())) {
            throw new DocumentValidationException("ONLYOFFICE_JWT_SECRET es obligatorio cuando OnlyOffice esta habilitado");
        }
        return Jwts.builder()
                .claims(payload)
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(onlyOfficeProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    private DocumentMetadata findDocument(String documentId, String tenantId) {
        if (!hasText(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }
        return documentMetadataRepository.findByIdAndTenantId(documentId.trim(), tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private TaskDocumentConfig resolveTaskConfig(DocumentMetadata document) {
        if (!hasText(document.getProcessKey()) || document.getProcessVersion() == null || !hasText(document.getTaskDefinitionKey())) {
            return null;
        }
        return taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        document.getTenantId(),
                        document.getProcessKey(),
                        document.getProcessVersion(),
                        document.getTaskDefinitionKey())
                .orElse(null);
    }

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = requesterEmail == null ? null : requesterEmail.trim();
        if (!hasText(normalized)) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (!hasText(usuario.getTenantId())) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private OnlyOfficeEditingSessionResponseDto editingResponse(DocumentMetadata document, boolean editing, String message) {
        return OnlyOfficeEditingSessionResponseDto.builder()
                .documentId(document.getId())
                .documentKey(document.getOnlyOfficeDocumentKey())
                .editing(editing)
                .currentEditor(document.getCurrentEditor())
                .editingStartedAt(document.getEditingStartedAt())
                .message(message)
                .build();
    }

    private OnlyOfficeCallbackResponseDto callbackOk(String message) {
        return OnlyOfficeCallbackResponseDto.builder().error(0).message(message).build();
    }

    private OnlyOfficeCallbackResponseDto callbackError(String message) {
        return OnlyOfficeCallbackResponseDto.builder().error(1).message(message).build();
    }

    private long resolveSignedUrlMinutes() {
        return documentProperties.signedUrl() != null && documentProperties.signedUrl().expirationMinutes() > 0
                ? documentProperties.signedUrl().expirationMinutes()
                : 15;
    }

    private String resolveCallbackEditor(DocumentMetadata document, OnlyOfficeCallbackRequestDto request) {
        if (hasText(document.getCurrentEditor())) {
            return document.getCurrentEditor();
        }
        if (request.getUsers() != null && !request.getUsers().isEmpty()) {
            return request.getUsers().get(0);
        }
        return "onlyoffice";
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
    }

    private String resolveDocumentType(String mimeType, String originalName) {
        String fileType = resolveFileType(originalName);
        if ("doc".equals(fileType) || "docx".equals(fileType) || "odt".equals(fileType) || "txt".equals(fileType) || "pdf".equals(fileType)) {
            return "word";
        }
        if ("xls".equals(fileType) || "xlsx".equals(fileType) || "csv".equals(fileType)) {
            return "cell";
        }
        if ("ppt".equals(fileType) || "pptx".equals(fileType)) {
            return "slide";
        }
        return mimeType != null && mimeType.contains("sheet") ? "cell" : "word";
    }

    private String resolveFileType(String originalName) {
        if (!hasText(originalName) || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String resolveExtension(String originalName) {
        String fileType = resolveFileType(originalName);
        return fileType == null ? "" : fileType.replaceAll("[^a-zA-Z0-9]", "");
    }

    private String sanitizeBaseUrl(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String sanitizeKey(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.=-]", "_");
    }

    private String extractBearer(String authorizationHeader) {
        if (!hasText(authorizationHeader)) {
            return null;
        }
        return authorizationHeader.toLowerCase().startsWith("bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
