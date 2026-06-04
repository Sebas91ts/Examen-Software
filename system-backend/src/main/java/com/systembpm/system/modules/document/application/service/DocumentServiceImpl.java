package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentDownloadUrlResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;
import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentSizeExceededException;
import com.systembpm.system.modules.document.domain.DocumentStatus;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentUrlExpiredException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.InvalidDocumentAccessException;
import com.systembpm.system.modules.document.infrastructure.config.DocumentProperties;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.FolderRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final DocumentStoragePort documentStoragePort;
    private final DocumentProperties documentProperties;
    private final UsuarioRepository usuarioRepository;
    private final TaskDocumentConfigService taskDocumentConfigService;
    private final FolderRepository folderRepository;

    @Override
    public DocumentUploadResponseDto upload(DocumentUploadRequestDto request, MultipartFile file, String uploadedBy) {
        Usuario requester = getRequester(uploadedBy);
        validateRequest(request, file);

        String requestedTenantId = normalize(request.getTenantId());
        enforceTenantMatchOrThrow(requestedTenantId, requester);
        String tenantId = requester.getTenantId();

        String processInstanceId = normalize(request.getProcessInstanceId());
        String originalName = normalizeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String folderId = validateAndResolveFolderId(request, tenantId);

        enforceTaskDocumentRulesIfPresent(request, requester, processInstanceId, contentType, file.getSize());

        int version = resolveNextVersion(tenantId, processInstanceId, originalName);
        String documentId = new ObjectId().toHexString();
        String extension = resolveExtension(originalName);
        String fileName = buildStoredFileName(documentId, version, extension);
        String s3Key = tenantId + "/" + processInstanceId + "/" + documentId + "/" + version + "/" + fileName;
        Instant now = Instant.now();

        try (InputStream inputStream = file.getInputStream()) {
            documentStoragePort.upload(s3Key, contentType, file.getSize(), inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo para subirlo a S3", ex);
        }

        DocumentMetadata metadata = DocumentMetadata.builder()
                .id(documentId)
                .tenantId(tenantId)
                .processInstanceId(processInstanceId)
                .fileName(fileName)
                .originalName(originalName)
                .mimeType(contentType)
                .size(file.getSize())
                .s3Key(s3Key)
                .uploadedBy(requester.getEmail())
                .uploadedAt(now)
                .version(version)
                .status(DocumentStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .updatedBy(requester.getEmail())
                .processKey(normalize(request.getProcessKey()))
                .processVersion(request.getProcessVersion())
                .taskDefinitionKey(normalize(request.getTaskDefinitionKey()))
                .taskInstanceId(normalize(request.getTaskInstanceId()))
                .documentState(DocumentLifecycleState.UPLOADED)
                .locked(false)
                .folderId(folderId)
                .build();

        DocumentMetadata saved;
        try {
            log.info(
                    "document.upload.persist tenantId={} processInstanceId={} documentId={} version={} user={} uploadedAt={} size={} mimeType={}",
                    tenantId,
                    processInstanceId,
                    documentId,
                    version,
                    requester.getEmail(),
                    metadata.getUploadedAt(),
                    metadata.getSize(),
                    metadata.getMimeType()
            );
            saved = documentMetadataRepository.save(metadata);
        } catch (RuntimeException ex) {
            documentStoragePort.delete(s3Key);
            throw ex;
        }

        log.info("document.upload.success tenantId={} processInstanceId={} documentId={} version={} size={}",
                saved.getTenantId(), saved.getProcessInstanceId(), saved.getId(), saved.getVersion(), saved.getSize());
        return toUploadResponse(saved);
    }

    @Override
    public DocumentMetadataResponseDto getById(String documentId, String requester) {
        Usuario authenticatedUser = getRequester(requester);
        DocumentMetadata metadata = findDocumentForRequester(documentId, authenticatedUser);
        enforceRoleAccess(metadata, authenticatedUser);
        DocumentMetadata updated = updateLastAccessed(metadata, authenticatedUser.getEmail());
        log.info("document.metadata.read tenantId={} processInstanceId={} documentId={} user={} lastAccessedAt={}",
                updated.getTenantId(), updated.getProcessInstanceId(), updated.getId(), authenticatedUser.getEmail(), updated.getLastAccessedAt());
        return toMetadataResponse(updated);
    }

    @Override
    public DocumentDownloadUrlResponseDto getDownloadUrl(String documentId, String requester) {
        Usuario authenticatedUser = getRequester(requester);
        DocumentMetadata metadata = findDocumentForRequester(documentId, authenticatedUser);
        enforceRoleAccess(metadata, authenticatedUser);
        validateAccess(metadata);

        long expirationMinutes = documentProperties.signedUrl() != null
                ? documentProperties.signedUrl().expirationMinutes()
                : 0;
        if (expirationMinutes <= 0) {
            throw new DocumentUrlExpiredException("La expiracion configurada para signed URLs es invalida");
        }

        PresignedDownloadUrl presignedUrl = documentStoragePort.generateDownloadUrl(
                metadata.getS3Key(),
                metadata.getOriginalName(),
                Duration.ofMinutes(expirationMinutes)
        );

        DocumentMetadata updated = updateLastAccessed(metadata, authenticatedUser.getEmail());
        log.info("document.download-url.generated tenantId={} processInstanceId={} documentId={} user={} expiresAt={} lastAccessedAt={}",
                updated.getTenantId(),
                updated.getProcessInstanceId(),
                updated.getId(),
                authenticatedUser.getEmail(),
                presignedUrl.expiresAt(),
                updated.getLastAccessedAt());

        return DocumentDownloadUrlResponseDto.builder()
                .documentId(updated.getId())
                .fileName(updated.getOriginalName())
                .downloadUrl(presignedUrl.url())
                .expiresAt(presignedUrl.expiresAt())
                .build();
    }

    @Override
    public List<DocumentMetadataResponseDto> getByProcessInstanceId(String processInstanceId, String requester) {
        if (isBlank(processInstanceId)) {
            throw new DocumentValidationException("processInstanceId es obligatorio");
        }

        Usuario authenticatedUser = getRequester(requester);
        String tenantId = authenticatedUser.getTenantId();
        List<DocumentMetadata> documents = isAdmin(authenticatedUser)
                ? documentMetadataRepository.findByProcessInstanceIdOrderByUploadedAtDesc(processInstanceId)
                : documentMetadataRepository.findByTenantIdAndProcessInstanceIdOrderByUploadedAtDesc(tenantId, processInstanceId);
        List<DocumentMetadataResponseDto> results = documents
                .stream()
                .filter(metadata -> canAccess(metadata, authenticatedUser))
                .map(this::toMetadataResponse)
                .toList();

        log.info("document.process.list tenantId={} processInstanceId={} user={} count={}",
                tenantId, processInstanceId, authenticatedUser.getEmail(), results.size());
        return results;
    }

    private Usuario getRequester(String requester) {
        String normalizedRequester = normalize(requester);
        if (normalizedRequester == null) {
            throw new DocumentRequesterNotFoundException();
        }

        Usuario usuario = usuarioRepository.findByEmail(normalizedRequester)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(usuario.getTenantId())) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private DocumentMetadata findDocument(String documentId, String tenantId) {
        if (isBlank(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }

        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (!tenantId.equals(metadata.getTenantId())) {
            log.warn("document.tenant.forbidden tenantId={} documentTenantId={} documentId={}", tenantId, metadata.getTenantId(), documentId);
            throw new DocumentTenantAccessDeniedException();
        }

        return metadata;
    }

    private DocumentMetadata findDocumentForRequester(String documentId, Usuario requester) {
        if (isAdmin(requester)) {
            if (isBlank(documentId)) {
                throw new DocumentValidationException("documentId es obligatorio");
            }
            return documentMetadataRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));
        }
        return findDocument(documentId, requester.getTenantId());
    }

    private DocumentMetadata updateLastAccessed(DocumentMetadata metadata, String updatedBy) {
        Instant now = Instant.now();
        metadata.setLastAccessedAt(now);
        metadata.setUpdatedAt(now);
        metadata.setUpdatedBy(updatedBy);
        return documentMetadataRepository.save(metadata);
    }

    private void validateAccess(DocumentMetadata metadata) {
        if (metadata.getStatus() == DocumentStatus.DELETED) {
            throw new InvalidDocumentAccessException("El documento no esta disponible para descarga");
        }
    }

    private void enforceRoleAccess(DocumentMetadata metadata, Usuario requester) {
        if (!canAccess(metadata, requester)) {
            log.warn("document.role.forbidden tenantId={} documentId={} user={} roles={}",
                    requester.getTenantId(), metadata.getId(), requester.getEmail(), requester.getRoles());
            throw new DocumentTenantAccessDeniedException();
        }
    }

    private boolean canAccess(DocumentMetadata metadata, Usuario requester) {
        if (isClient(requester)) {
            return requester.getEmail() != null && requester.getEmail().equalsIgnoreCase(metadata.getUploadedBy());
        }
        return true;
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    private void validateRequest(DocumentUploadRequestDto request, MultipartFile file) {
        if (request == null) {
            throw new DocumentValidationException("La solicitud de documento es obligatoria");
        }
        if (isBlank(request.getTenantId())) {
            throw new DocumentValidationException("tenantId es obligatorio");
        }
        if (isBlank(request.getProcessInstanceId())) {
            throw new DocumentValidationException("processInstanceId es obligatorio");
        }
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new DocumentValidationException("Debes enviar un archivo valido");
        }
        long maxFileSizeBytes = documentProperties.maxFileSizeBytes();
        if (maxFileSizeBytes > 0 && file.getSize() > maxFileSizeBytes) {
            throw new DocumentSizeExceededException(maxFileSizeBytes);
        }
        String originalName = normalizeFilename(file.getOriginalFilename());
        if (isBlank(originalName)) {
            throw new DocumentValidationException("El nombre del archivo es obligatorio");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!isAllowedContentType(contentType)) {
            throw new DocumentValidationException("El tipo de archivo no esta soportado");
        }
    }

    private void enforceTenantMatchOrThrow(String requestedTenantId, Usuario requester) {
        if (isBlank(requestedTenantId)) {
            throw new DocumentValidationException("tenantId es obligatorio");
        }
        if (!requestedTenantId.equals(requester.getTenantId())) {
            log.warn("document.tenant.mismatch requestedTenantId={} userTenantId={} user={}",
                    requestedTenantId, requester.getTenantId(), requester.getEmail());
            throw new DocumentTenantAccessDeniedException();
        }
    }

    private void enforceTaskDocumentRulesIfPresent(
            DocumentUploadRequestDto request,
            Usuario requester,
            String processInstanceId,
            String mimeType,
            long size
    ) {
        String processKey = normalize(request.getProcessKey());
        Integer version = request.getProcessVersion();
        String taskDefinitionKey = normalize(request.getTaskDefinitionKey());
        if (processKey == null || version == null || taskDefinitionKey == null) {
            return;
        }

        TaskDocumentUploadValidationResponseDto validation = taskDocumentConfigService.validateUpload(
                TaskDocumentUploadValidationRequestDto.builder()
                        .processKey(processKey)
                        .processVersion(version)
                        .taskDefinitionKey(taskDefinitionKey)
                        .processInstanceId(processInstanceId)
                        .mimeType(mimeType)
                        .size(size)
                        .build(),
                requester.getEmail()
        );

        if (!validation.isAllowed()) {
            String reason = validation.getReason() == null ? "Restriccion documental" : validation.getReason();
            // Permisos => 403, restricciones => 400
            if (reason.toLowerCase(Locale.ROOT).contains("permiso")) {
                throw new InvalidDocumentAccessException(reason);
            }
            throw new DocumentValidationException(reason);
        }
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        List<String> allowedTypes = documentProperties.allowedContentTypes();
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return false;
        }
        return allowedTypes.stream().anyMatch(allowed -> matchesAllowedType(contentType, allowed));
    }

    private boolean matchesAllowedType(String contentType, String allowedType) {
        if (allowedType == null || allowedType.isBlank()) {
            return false;
        }
        String normalizedAllowedType = allowedType.trim().toLowerCase(Locale.ROOT);
        if (normalizedAllowedType.endsWith("/*")) {
            String prefix = normalizedAllowedType.substring(0, normalizedAllowedType.length() - 1);
            return contentType.startsWith(prefix);
        }
        return contentType.equals(normalizedAllowedType);
    }

    private int resolveNextVersion(String tenantId, String processInstanceId, String originalName) {
        Optional<DocumentMetadata> lastVersion = documentMetadataRepository
                .findTopByTenantIdAndProcessInstanceIdAndOriginalNameOrderByVersionDesc(tenantId, processInstanceId, originalName);
        return lastVersion.map(metadata -> metadata.getVersion() + 1).orElse(1);
    }

    private DocumentUploadResponseDto toUploadResponse(DocumentMetadata metadata) {
        return DocumentUploadResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .processInstanceId(metadata.getProcessInstanceId())
                .fileName(metadata.getFileName())
                .originalName(metadata.getOriginalName())
                .mimeType(metadata.getMimeType())
                .size(metadata.getSize())
                .s3Key(metadata.getS3Key())
                .uploadedBy(metadata.getUploadedBy())
                .uploadedAt(metadata.getUploadedAt())
                .version(metadata.getVersion())
                .status(metadata.getStatus())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .lastAccessedAt(metadata.getLastAccessedAt())
                .updatedBy(metadata.getUpdatedBy())
                .processKey(metadata.getProcessKey())
                .processVersion(metadata.getProcessVersion())
                .taskDefinitionKey(metadata.getTaskDefinitionKey())
                .taskInstanceId(metadata.getTaskInstanceId())
                .documentState(metadata.getDocumentState())
                .locked(metadata.getLocked())
                .lockedBy(metadata.getLockedBy())
                .lockedAt(metadata.getLockedAt())
                .approvedBy(metadata.getApprovedBy())
                .approvedAt(metadata.getApprovedAt())
                .rejectedBy(metadata.getRejectedBy())
                .rejectedAt(metadata.getRejectedAt())
                .comments(metadata.getComments())
                .folderId(metadata.getFolderId())
                .tagIds(metadata.getTagIds())
                .editable(metadata.getEditable())
                .collaborativeEditing(metadata.getCollaborativeEditing())
                .onlyOfficeDocumentKey(metadata.getOnlyOfficeDocumentKey())
                .templateDocumentId(metadata.getTemplateDocumentId())
                .currentEditor(metadata.getCurrentEditor())
                .editingStartedAt(metadata.getEditingStartedAt())
                .build();
    }

    private DocumentMetadataResponseDto toMetadataResponse(DocumentMetadata metadata) {
        return DocumentMetadataResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .processInstanceId(metadata.getProcessInstanceId())
                .fileName(metadata.getFileName())
                .originalName(metadata.getOriginalName())
                .mimeType(metadata.getMimeType())
                .size(metadata.getSize())
                .s3Key(metadata.getS3Key())
                .uploadedBy(metadata.getUploadedBy())
                .uploadedAt(metadata.getUploadedAt())
                .version(metadata.getVersion())
                .status(metadata.getStatus())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .lastAccessedAt(metadata.getLastAccessedAt())
                .updatedBy(metadata.getUpdatedBy())
                .processKey(metadata.getProcessKey())
                .processVersion(metadata.getProcessVersion())
                .taskDefinitionKey(metadata.getTaskDefinitionKey())
                .taskInstanceId(metadata.getTaskInstanceId())
                .documentState(metadata.getDocumentState())
                .locked(metadata.getLocked())
                .lockedBy(metadata.getLockedBy())
                .lockedAt(metadata.getLockedAt())
                .approvedBy(metadata.getApprovedBy())
                .approvedAt(metadata.getApprovedAt())
                .rejectedBy(metadata.getRejectedBy())
                .rejectedAt(metadata.getRejectedAt())
                .comments(metadata.getComments())
                .folderId(metadata.getFolderId())
                .tagIds(metadata.getTagIds())
                .editable(metadata.getEditable())
                .collaborativeEditing(metadata.getCollaborativeEditing())
                .onlyOfficeDocumentKey(metadata.getOnlyOfficeDocumentKey())
                .templateDocumentId(metadata.getTemplateDocumentId())
                .currentEditor(metadata.getCurrentEditor())
                .editingStartedAt(metadata.getEditingStartedAt())
                .build();
    }

    private String validateAndResolveFolderId(DocumentUploadRequestDto request, String tenantId) {
        String folderId = normalize(request.getFolderId());
        if (folderId == null) {
            return null;
        }
        if (!folderRepository.existsByIdAndTenantIdAndActiveTrue(folderId, tenantId)) {
            throw new DocumentValidationException("folderId no existe o no pertenece a tu tenant");
        }
        return folderId;
    }

    private String buildStoredFileName(String documentId, int version, String extension) {
        String baseName = documentId + "-v" + version;
        return extension.isBlank() ? baseName : baseName + "." + extension;
    }

    private String resolveExtension(String originalName) {
        int index = originalName.lastIndexOf('.');
        if (index < 0 || index == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(index + 1)
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeFilename(String originalName) {
        String normalized = normalize(originalName);
        if (normalized == null) {
            return "document-" + UUID.randomUUID();
        }
        String sanitized = normalized
                .replace("\\", "_")
                .replace("/", "_")
                .replace("..", "_")
                .replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() > 255 ? sanitized.substring(0, 255) : sanitized;
    }

    private String normalizeContentType(String contentType) {
        String normalized = normalize(contentType);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
