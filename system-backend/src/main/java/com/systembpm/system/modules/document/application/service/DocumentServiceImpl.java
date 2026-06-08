package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentDownloadUrlResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentAreaAccessRuleDto;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentUploadResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;
import com.systembpm.system.modules.document.application.port.out.DocumentStoragePort;
import com.systembpm.system.modules.document.application.port.out.PresignedDownloadUrl;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.DocumentRequirement;
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
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.xml.sax.InputSource;

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
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final ProcesoRepository procesoRepository;

    @Override
    public DocumentUploadResponseDto upload(DocumentUploadRequestDto request, MultipartFile file, String uploadedBy) {
        Usuario requester = getRequester(uploadedBy);
        validateRequest(request, file);

        String requestedTenantId = normalize(request.getTenantId());
        enforceTenantMatchOrThrow(requestedTenantId, requester);
        String tenantId = isAdmin(requester) ? requestedTenantId : effectiveAreaId(requester);

        String processInstanceId = normalize(request.getProcessInstanceId());
        String originalName = normalizeFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String folderId = validateAndResolveFolderId(request, tenantId);

        enforceTaskDocumentRulesIfPresent(request, requester, processInstanceId, contentType, file.getSize());
        var taskConfig = resolveTaskConfig(request, tenantId);
        DocumentRequirement requirement = selectRequirement(taskConfig, request.getDocumentRequirementId(), contentType, file.getSize());
        String ownerAreaId = !isBlank(requirement != null ? requirement.getOwnerAreaId() : null)
                ? requirement.getOwnerAreaId()
                : !isBlank(taskConfig != null ? taskConfig.getOwnerAreaId() : null)
                ? taskConfig.getOwnerAreaId()
                : tenantId;
        List<String> allowedAreaIds = requirement != null && requirement.getAllowedAreaIds() != null
                ? requirement.getAllowedAreaIds()
                : taskConfig != null && taskConfig.getAllowedAreaIds() != null
                ? taskConfig.getAllowedAreaIds()
                : List.of();
        List<DocumentAreaAccessRule> accessRules = requirement != null && requirement.getAccessRules() != null
                ? requirement.getAccessRules()
                : taskConfig != null && taskConfig.getAccessRules() != null
                ? taskConfig.getAccessRules()
                : List.of();
        List<DocumentAreaAccessRule> propagatedAccessRules = propagateAccessRulesToConsumers(
                request,
                requirement,
                accessRules,
                ownerAreaId,
                originalName
        );
        List<String> propagatedAllowedAreaIds = mergeAllowedAreaIds(allowedAreaIds, propagatedAccessRules, ownerAreaId);

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
                .ownerAreaId(ownerAreaId)
                .allowedAreaIds(propagatedAllowedAreaIds)
                .accessRules(propagatedAccessRules)
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
                .documentRequirementId(requirement != null ? requirement.getId() : normalize(request.getDocumentRequirementId()))
                .documentRequirementName(requirement != null ? requirement.getName() : null)
                .documentDirection(requirement != null ? requirement.getDocumentDirection() : null)
                .documentLifecyclePolicy(requirement != null ? requirement.getDocumentLifecyclePolicy() : null)
                .documentState(DocumentLifecycleState.UPLOADED)
                .locked(false)
                .folderId(folderId)
                .editable(requirement != null ? requirement.getEditable() : taskConfig != null ? taskConfig.getEditable() : null)
                .collaborativeEditing(requirement != null ? requirement.getCollaborativeEditing() : taskConfig != null ? taskConfig.getCollaborativeEditing() : null)
                .templateDocumentId(taskConfig != null ? taskConfig.getTemplateDocumentId() : null)
                .build();

        DocumentMetadata saved;
        try {
            log.info(
                    "document.upload.persist tenantId={} processInstanceId={} documentId={} version={} user={} uploadedAt={} size={} mimeType={} ownerAreaId={} sharedAreas={}",
                    tenantId,
                    processInstanceId,
                    documentId,
                    version,
                    requester.getEmail(),
                    metadata.getUploadedAt(),
                    metadata.getSize(),
                    metadata.getMimeType(),
                    metadata.getOwnerAreaId(),
                    metadata.getAllowedAreaIds()
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
        enforceAreaPermission(metadata, authenticatedUser, DocumentPermission.VIEW);
        DocumentMetadata updated = updateLastAccessed(metadata, authenticatedUser.getEmail());
        log.info("document.metadata.read tenantId={} processInstanceId={} documentId={} user={} lastAccessedAt={}",
                updated.getTenantId(), updated.getProcessInstanceId(), updated.getId(), authenticatedUser.getEmail(), updated.getLastAccessedAt());
        return toMetadataResponse(updated);
    }

    @Override
    public DocumentDownloadUrlResponseDto getDownloadUrl(String documentId, String requester) {
        Usuario authenticatedUser = getRequester(requester);
        DocumentMetadata metadata = findDocumentForRequester(documentId, authenticatedUser);
        enforceAreaPermission(metadata, authenticatedUser, DocumentPermission.DOWNLOAD);
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
        String tenantId = effectiveAreaId(authenticatedUser);
        List<DocumentMetadata> documents = documentMetadataRepository.findByProcessInstanceIdOrderByUploadedAtDesc(processInstanceId);
        List<DocumentMetadataResponseDto> results = documents
                .stream()
                .filter(metadata -> hasAreaPermission(metadata, authenticatedUser, DocumentPermission.VIEW))
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
        if (isBlank(effectiveAreaId(usuario)) && !isAdmin(usuario)) {
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
        if (isBlank(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }
        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!hasAreaPermission(metadata, requester, DocumentPermission.VIEW)) {
            log.warn("document.area-access.forbidden userAreaId={} ownerAreaId={} allowedAreaIds={} documentId={} user={} roles={}",
                    requester.getTenantId(), ownerArea(metadata), metadata.getAllowedAreaIds(), documentId, requester.getEmail(), requester.getRoles());
            throw new DocumentTenantAccessDeniedException();
        }
        return metadata;
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

    private void enforceAreaPermission(DocumentMetadata metadata, Usuario requester, DocumentPermission permission) {
        if (!hasAreaPermission(metadata, requester, permission)) {
            log.warn("document.area-permission.forbidden tenantId={} ownerAreaId={} allowedAreaIds={} permission={} userAreaId={} documentId={} user={} roles={}",
                    metadata.getTenantId(), ownerArea(metadata), metadata.getAllowedAreaIds(), permission,
                    requester.getTenantId(), metadata.getId(), requester.getEmail(), requester.getRoles());
            throw new DocumentTenantAccessDeniedException();
        }
    }

    private boolean hasAreaPermission(DocumentMetadata metadata, Usuario requester, DocumentPermission permission) {
        if (isAdmin(requester)) {
            return true;
        }
        if (isClient(requester)) {
            return requester.getEmail() != null && requester.getEmail().equalsIgnoreCase(metadata.getUploadedBy())
                    && (permission == DocumentPermission.VIEW || permission == DocumentPermission.DOWNLOAD);
        }
        String areaId = effectiveAreaId(requester);
        if (areaId == null) {
            return false;
        }
        if (areaId.equals(ownerArea(metadata)) || areaId.equals(metadata.getTenantId())) {
            return true;
        }
        DocumentAreaAccessRule rule = findRule(metadata, areaId);
        if (rule != null) {
            return switch (permission) {
                case VIEW -> Boolean.TRUE.equals(rule.getCanView());
                case DOWNLOAD -> Boolean.TRUE.equals(rule.getCanDownload()) || Boolean.TRUE.equals(rule.getCanView());
                case EDIT -> Boolean.TRUE.equals(rule.getCanEdit());
                case APPROVE -> Boolean.TRUE.equals(rule.getCanApprove());
                case REJECT -> Boolean.TRUE.equals(rule.getCanReject());
                case LOCK -> Boolean.TRUE.equals(rule.getCanLock());
            };
        }
        return (permission == DocumentPermission.VIEW || permission == DocumentPermission.DOWNLOAD)
                && metadata.getAllowedAreaIds() != null
                && metadata.getAllowedAreaIds().contains(areaId);
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    private String effectiveAreaId(Usuario usuario) {
        if (!isBlank(usuario.getAreaId())) {
            return usuario.getAreaId().trim();
        }
        return isBlank(usuario.getTenantId()) ? null : usuario.getTenantId().trim();
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
        String requesterAreaId = effectiveAreaId(requester);
        if (!isAdmin(requester) && !requestedTenantId.equals(requesterAreaId)) {
            log.warn("document.tenant.mismatch requestedTenantId={} userTenantId={} user={}",
                    requestedTenantId, requesterAreaId, requester.getEmail());
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
                        .documentRequirementId(normalize(request.getDocumentRequirementId()))
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
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(toAccessRuleDtos(metadata.getAccessRules()))
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
                .documentRequirementId(metadata.getDocumentRequirementId())
                .documentRequirementName(metadata.getDocumentRequirementName())
                .documentDirection(metadata.getDocumentDirection())
                .documentLifecyclePolicy(metadata.getDocumentLifecyclePolicy())
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
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(toAccessRuleDtos(metadata.getAccessRules()))
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
                .documentRequirementId(metadata.getDocumentRequirementId())
                .documentRequirementName(metadata.getDocumentRequirementName())
                .documentDirection(metadata.getDocumentDirection())
                .documentLifecyclePolicy(metadata.getDocumentLifecyclePolicy())
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

    private com.systembpm.system.modules.document.domain.TaskDocumentConfig resolveTaskConfig(DocumentUploadRequestDto request, String tenantId) {
        String processKey = normalize(request.getProcessKey());
        Integer version = request.getProcessVersion();
        String taskDefinitionKey = normalize(request.getTaskDefinitionKey());
        if (processKey == null || version == null || taskDefinitionKey == null) {
            return null;
        }
        return taskDocumentConfigRepository
                .findFirstByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCaseOrderByUpdatedAtDesc(
                        processKey,
                        version,
                        taskDefinitionKey)
                .orElse(null);
    }

    private DocumentRequirement selectRequirement(
            com.systembpm.system.modules.document.domain.TaskDocumentConfig config,
            String requirementId,
            String mimeType,
            long size
    ) {
        if (config == null) {
            return null;
        }
        List<DocumentRequirement> requirements = resolveRequirements(config);
        if (requirements.isEmpty()) {
            return null;
        }
        if (!isBlank(requirementId)) {
            return requirements.stream()
                    .filter(requirement -> requirementId.trim().equals(requirement.getId()))
                    .findFirst()
                    .orElse(null);
        }
        return requirements.stream()
                .filter(requirement -> requirement.getAllowedMimeTypes() == null || requirement.getAllowedMimeTypes().isEmpty()
                        || requirement.getAllowedMimeTypes().stream().anyMatch(rule -> matchesAllowedType(mimeType, rule)))
                .filter(requirement -> requirement.getMaxFileSizeBytes() == null || requirement.getMaxFileSizeBytes() <= 0 || size <= requirement.getMaxFileSizeBytes())
                .findFirst()
                .orElse(requirements.get(0));
    }

    private List<DocumentRequirement> resolveRequirements(com.systembpm.system.modules.document.domain.TaskDocumentConfig config) {
        if (config.getDocumentRequirements() != null && !config.getDocumentRequirements().isEmpty()) {
            return config.getDocumentRequirements();
        }
        return List.of(DocumentRequirement.builder()
                .id("legacy-main-document")
                .name(config.getDocumentName())
                .description(config.getDescription())
                .documentDirection(config.getDocumentDirection())
                .required(config.getRequired())
                .allowUpload(config.getAllowUpload())
                .allowMultipleFiles(config.getAllowMultipleFiles())
                .editable(config.getEditable())
                .collaborativeEditing(config.getCollaborativeEditing())
                .requireApproval(config.getRequireApproval())
                .readOnlyAfterComplete(config.getReadOnlyAfterComplete())
                .allowedMimeTypes(config.getAllowedMimeTypes())
                .maxFileSizeBytes(config.getMaxFileSizeBytes())
                .maxFiles(config.getMaxFiles())
                .ownerAreaId(config.getOwnerAreaId())
                .allowedAreaIds(config.getAllowedAreaIds())
                .accessRules(config.getAccessRules())
                .documentLifecyclePolicy(defaultLifecyclePolicy(config.getDocumentDirection()))
                .build());
    }

    private List<DocumentAreaAccessRule> propagateAccessRulesToConsumers(
            DocumentUploadRequestDto request,
            DocumentRequirement sourceRequirement,
            List<DocumentAreaAccessRule> existingRules,
            String ownerAreaId,
            String originalName
    ) {
        List<DocumentAreaAccessRule> mergedRules = new ArrayList<>(existingRules == null ? List.of() : existingRules);
        if (sourceRequirement == null || !"OUTPUT".equalsIgnoreCase(normalize(sourceRequirement.getDocumentDirection()))) {
            return mergedRules;
        }
        if (!canTravelToLaterTasks(sourceRequirement.getDocumentLifecyclePolicy())) {
            return mergedRules;
        }

        String processKey = normalize(request.getProcessKey());
        Integer processVersion = request.getProcessVersion();
        if (isBlank(processKey) || processVersion == null) {
            return mergedRules;
        }

        String currentTaskDefinitionKey = normalize(request.getTaskDefinitionKey());
        String sourceName = normalizeComparable(sourceRequirement.getName());
        if (isBlank(sourceName)) {
            sourceName = normalizeComparable(stripExtension(originalName));
        }

        Proceso proceso = procesoRepository.findByProcessKey(processKey).stream()
                .filter(item -> processVersion.equals(item.getVersion()))
                .findFirst()
                .orElse(null);

        List<com.systembpm.system.modules.document.domain.TaskDocumentConfig> configs = taskDocumentConfigRepository
                .findByProcessKeyIgnoreCaseAndProcessVersion(processKey, processVersion);

        for (com.systembpm.system.modules.document.domain.TaskDocumentConfig config : configs) {
            if (config == null || currentTaskDefinitionKey != null && currentTaskDefinitionKey.equalsIgnoreCase(nullToEmpty(config.getTaskDefinitionKey()))) {
                continue;
            }
            for (DocumentRequirement targetRequirement : resolveRequirements(config)) {
                if (!"INPUT".equalsIgnoreCase(normalize(targetRequirement.getDocumentDirection()))) {
                    continue;
                }
                if (!sameRequirementBusinessName(sourceName, targetRequirement)) {
                    continue;
                }
                for (DocumentAreaAccessRule rule : buildConsumerRules(proceso, config, targetRequirement, ownerAreaId)) {
                    upsertAccessRule(mergedRules, rule);
                }
            }
        }

        return mergedRules;
    }

    private boolean sameRequirementBusinessName(String sourceName, DocumentRequirement targetRequirement) {
        String targetName = normalizeComparable(targetRequirement.getName());
        return !isBlank(sourceName) && sourceName.equals(targetName);
    }

    private List<DocumentAreaAccessRule> buildConsumerRules(
            Proceso proceso,
            com.systembpm.system.modules.document.domain.TaskDocumentConfig targetConfig,
            DocumentRequirement targetRequirement,
            String ownerAreaId
    ) {
        List<DocumentAreaAccessRule> rules = new ArrayList<>();
        if (targetRequirement.getAccessRules() != null) {
            rules.addAll(targetRequirement.getAccessRules());
        }
        if (targetConfig.getAccessRules() != null) {
            rules.addAll(targetConfig.getAccessRules());
        }

        Set<String> areaIds = new LinkedHashSet<>();
        areaIds.addAll(targetRequirement.getAllowedAreaIds() == null ? List.of() : targetRequirement.getAllowedAreaIds());
        areaIds.addAll(targetConfig.getAllowedAreaIds() == null ? List.of() : targetConfig.getAllowedAreaIds());
        areaIds.add(normalize(targetRequirement.getOwnerAreaId()));
        areaIds.add(normalize(targetConfig.getOwnerAreaId()));
        areaIds.add(resolveTaskAreaFromProcess(proceso, targetConfig.getTaskDefinitionKey()));
        areaIds.removeIf(areaId -> isBlank(areaId) || areaId.equals(ownerAreaId));

        for (String areaId : areaIds) {
            if (rules.stream().noneMatch(rule -> rule != null && areaId.equals(rule.getAreaId()))) {
                rules.add(DocumentAreaAccessRule.builder()
                        .areaId(areaId)
                        .canView(true)
                        .canDownload(true)
                        .canUpload(Boolean.TRUE.equals(targetRequirement.getAllowUpload()))
                        .canEdit(Boolean.TRUE.equals(targetRequirement.getEditable()))
                        .canApprove(Boolean.TRUE.equals(targetRequirement.getRequireApproval()))
                        .canReject(Boolean.TRUE.equals(targetRequirement.getRequireApproval()))
                        .canLock(Boolean.TRUE.equals(targetRequirement.getEditable()))
                        .build());
            }
        }
        return rules;
    }

    private void upsertAccessRule(List<DocumentAreaAccessRule> rules, DocumentAreaAccessRule incoming) {
        if (incoming == null || isBlank(incoming.getAreaId())) {
            return;
        }
        for (DocumentAreaAccessRule current : rules) {
            if (current != null && incoming.getAreaId().equals(current.getAreaId())) {
                current.setCanView(Boolean.TRUE.equals(current.getCanView()) || Boolean.TRUE.equals(incoming.getCanView()));
                current.setCanDownload(Boolean.TRUE.equals(current.getCanDownload()) || Boolean.TRUE.equals(incoming.getCanDownload()));
                current.setCanUpload(Boolean.TRUE.equals(current.getCanUpload()) || Boolean.TRUE.equals(incoming.getCanUpload()));
                current.setCanEdit(Boolean.TRUE.equals(current.getCanEdit()) || Boolean.TRUE.equals(incoming.getCanEdit()));
                current.setCanApprove(Boolean.TRUE.equals(current.getCanApprove()) || Boolean.TRUE.equals(incoming.getCanApprove()));
                current.setCanReject(Boolean.TRUE.equals(current.getCanReject()) || Boolean.TRUE.equals(incoming.getCanReject()));
                current.setCanLock(Boolean.TRUE.equals(current.getCanLock()) || Boolean.TRUE.equals(incoming.getCanLock()));
                return;
            }
        }
        rules.add(incoming);
    }

    private List<String> mergeAllowedAreaIds(List<String> allowedAreaIds, List<DocumentAreaAccessRule> accessRules, String ownerAreaId) {
        Set<String> areas = new LinkedHashSet<>(allowedAreaIds == null ? List.of() : allowedAreaIds);
        for (DocumentAreaAccessRule rule : accessRules == null ? List.<DocumentAreaAccessRule>of() : accessRules) {
            if (rule != null && !isBlank(rule.getAreaId())) {
                areas.add(rule.getAreaId());
            }
        }
        areas.removeIf(areaId -> isBlank(areaId) || areaId.equals(ownerAreaId));
        return List.copyOf(areas);
    }

    private boolean canTravelToLaterTasks(String lifecyclePolicy) {
        String normalized = normalize(lifecyclePolicy);
        return "AVAILABLE_FOR_NEXT_TASKS".equalsIgnoreCase(normalized)
                || "AVAILABLE_FOR_INSTANCE".equalsIgnoreCase(normalized)
                || "PUBLISH_ON_PROCESS_END".equalsIgnoreCase(normalized);
    }

    private String resolveTaskAreaFromProcess(Proceso proceso, String taskDefinitionKey) {
        if (proceso == null || isBlank(proceso.getXml()) || isBlank(taskDefinitionKey)) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(proceso.getXml())));
            NodeList lanes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "lane");
            for (int i = 0; i < lanes.getLength(); i++) {
                Element lane = (Element) lanes.item(i);
                if (laneContainsTask(lane, taskDefinitionKey)) {
                    return readAreaIdFromLane(lane);
                }
            }
        } catch (Exception ex) {
            log.warn("document.upload.area-propagation.xml-failed processKey={} version={} taskDefinitionKey={}",
                    proceso.getProcessKey(), proceso.getVersion(), taskDefinitionKey, ex);
        }
        return null;
    }

    private boolean laneContainsTask(Element lane, String taskDefinitionKey) {
        NodeList flowNodeRefs = lane.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "flowNodeRef");
        for (int i = 0; i < flowNodeRefs.getLength(); i++) {
            String value = flowNodeRefs.item(i).getTextContent();
            if (taskDefinitionKey.equals(normalize(value))) {
                return true;
            }
        }
        return false;
    }

    private String readAreaIdFromLane(Element lane) {
        NodeList areaRefs = lane.getElementsByTagNameNS("http://systembpm.com/schema", "areaRef");
        if (areaRefs.getLength() == 0) {
            return null;
        }
        return normalize(areaRefs.item(0).getTextContent());
    }

    private String defaultLifecyclePolicy(String documentDirection) {
        return "OUTPUT".equalsIgnoreCase(normalize(documentDirection))
                ? "AVAILABLE_FOR_NEXT_TASKS"
                : "TASK_ONLY";
    }

    private String stripExtension(String fileName) {
        String normalized = normalize(fileName);
        if (normalized == null) {
            return null;
        }
        int index = normalized.lastIndexOf('.');
        return index <= 0 ? normalized : normalized.substring(0, index);
    }

    private String ownerArea(DocumentMetadata metadata) {
        return !isBlank(metadata.getOwnerAreaId()) ? metadata.getOwnerAreaId() : metadata.getTenantId();
    }

    private DocumentAreaAccessRule findRule(DocumentMetadata metadata, String areaId) {
        if (metadata.getAccessRules() == null || areaId == null) {
            return null;
        }
        return metadata.getAccessRules().stream()
                .filter(rule -> rule != null && areaId.equals(rule.getAreaId()))
                .findFirst()
                .orElse(null);
    }

    private List<DocumentAreaAccessRuleDto> toAccessRuleDtos(List<DocumentAreaAccessRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> DocumentAreaAccessRuleDto.builder()
                        .areaId(rule.getAreaId())
                        .canView(rule.getCanView())
                        .canUpload(rule.getCanUpload())
                        .canEdit(rule.getCanEdit())
                        .canDownload(rule.getCanDownload())
                        .canApprove(rule.getCanApprove())
                        .canReject(rule.getCanReject())
                        .canLock(rule.getCanLock())
                        .build())
                .toList();
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

    private String normalizeComparable(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private enum DocumentPermission {
        VIEW,
        DOWNLOAD,
        EDIT,
        APPROVE,
        REJECT,
        LOCK
    }
}
