package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.InvalidDocumentAccessException;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLifecycleServiceImpl implements DocumentLifecycleService {

    private static final List<DocumentLifecycleState> REVIEW_PENDING_STATES = List.of(
            DocumentLifecycleState.UPLOADED,
            DocumentLifecycleState.IN_REVIEW
    );

    private final DocumentMetadataRepository documentMetadataRepository;
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public DocumentMetadataResponseDto approve(String documentId, String comment, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata metadata = findDocumentForRequester(documentId, requester);
        enforceAreaPermission(metadata, requester, DocumentPermission.APPROVE);

        Instant now = Instant.now();
        metadata.setDocumentState(DocumentLifecycleState.APPROVED);
        metadata.setApprovedBy(requester.getEmail());
        metadata.setApprovedAt(now);
        metadata.setRejectedBy(null);
        metadata.setRejectedAt(null);
        metadata.setUpdatedAt(now);
        metadata.setUpdatedBy(requester.getEmail());
        addComment(metadata, requester.getEmail(), comment);

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        log.info("document.lifecycle.approve tenantId={} processInstanceId={} taskDefinitionKey={} taskInstanceId={} documentId={} user={}",
                saved.getTenantId(), saved.getProcessInstanceId(), saved.getTaskDefinitionKey(), saved.getTaskInstanceId(), saved.getId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public DocumentMetadataResponseDto reject(String documentId, String comment, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata metadata = findDocumentForRequester(documentId, requester);
        enforceAreaPermission(metadata, requester, DocumentPermission.REJECT);

        Instant now = Instant.now();
        metadata.setDocumentState(DocumentLifecycleState.REJECTED);
        metadata.setRejectedBy(requester.getEmail());
        metadata.setRejectedAt(now);
        metadata.setApprovedBy(null);
        metadata.setApprovedAt(null);
        metadata.setUpdatedAt(now);
        metadata.setUpdatedBy(requester.getEmail());
        addComment(metadata, requester.getEmail(), comment);

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        log.info("document.lifecycle.reject tenantId={} processInstanceId={} taskDefinitionKey={} taskInstanceId={} documentId={} user={}",
                saved.getTenantId(), saved.getProcessInstanceId(), saved.getTaskDefinitionKey(), saved.getTaskInstanceId(), saved.getId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public DocumentMetadataResponseDto lock(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata metadata = findDocumentForRequester(documentId, requester);
        enforceAreaPermission(metadata, requester, DocumentPermission.LOCK);
        ensureEditable(metadata, requester.getEmail());

        if (Boolean.TRUE.equals(metadata.getLocked()) && !requester.getEmail().equalsIgnoreCase(nullSafe(metadata.getLockedBy()))) {
            throw new InvalidDocumentAccessException("El documento esta bloqueado por otro usuario");
        }

        Instant now = Instant.now();
        metadata.setLocked(true);
        metadata.setLockedBy(requester.getEmail());
        metadata.setLockedAt(now);
        metadata.setUpdatedAt(now);
        metadata.setUpdatedBy(requester.getEmail());

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        log.info("document.lifecycle.lock tenantId={} processInstanceId={} taskDefinitionKey={} taskInstanceId={} documentId={} user={}",
                saved.getTenantId(), saved.getProcessInstanceId(), saved.getTaskDefinitionKey(), saved.getTaskInstanceId(), saved.getId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public DocumentMetadataResponseDto unlock(String documentId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        DocumentMetadata metadata = findDocumentForRequester(documentId, requester);
        enforceAreaPermission(metadata, requester, DocumentPermission.LOCK);

        if (Boolean.TRUE.equals(metadata.getLocked()) && !requester.getEmail().equalsIgnoreCase(nullSafe(metadata.getLockedBy())) && !isAdmin(requester)) {
            throw new InvalidDocumentAccessException("Solo quien bloqueo el documento o un administrador puede desbloquearlo");
        }

        Instant now = Instant.now();
        metadata.setLocked(false);
        metadata.setLockedBy(null);
        metadata.setLockedAt(null);
        metadata.setUpdatedAt(now);
        metadata.setUpdatedBy(requester.getEmail());

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        log.info("document.lifecycle.unlock tenantId={} processInstanceId={} taskDefinitionKey={} taskInstanceId={} documentId={} user={}",
                saved.getTenantId(), saved.getProcessInstanceId(), saved.getTaskDefinitionKey(), saved.getTaskInstanceId(), saved.getId(), requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public List<DocumentMetadataResponseDto> getByTask(String processInstanceId, String taskDefinitionKey, String taskInstanceId, String requesterEmail) {
        if (isBlank(processInstanceId) || isBlank(taskDefinitionKey)) {
            throw new DocumentValidationException("processInstanceId y taskDefinitionKey son obligatorios");
        }

        Usuario requester = resolveRequester(requesterEmail);
        List<DocumentMetadata> documents = isBlank(taskInstanceId)
                ? documentMetadataRepository.findByProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
                        processInstanceId.trim(), taskDefinitionKey.trim())
                : documentMetadataRepository.findByProcessInstanceIdAndTaskDefinitionKeyAndTaskInstanceIdOrderByUploadedAtDesc(
                        processInstanceId.trim(), taskDefinitionKey.trim(), taskInstanceId.trim());
        documents = documents.stream()
                .filter(document -> hasAreaPermission(document, requester, DocumentPermission.VIEW))
                .toList();

        log.info("document.lifecycle.task-list tenantId={} processInstanceId={} taskDefinitionKey={} taskInstanceId={} user={} count={}",
                requester.getTenantId(), processInstanceId, taskDefinitionKey, taskInstanceId, requester.getEmail(), documents.size());
        return documents.stream().map(this::toResponse).toList();
    }

    @Override
    public List<DocumentMetadataResponseDto> getPending(String processInstanceId, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        List<DocumentMetadata> documents = isBlank(processInstanceId)
                ? documentMetadataRepository.findByDocumentStateInOrderByUpdatedAtDesc(REVIEW_PENDING_STATES)
                : documentMetadataRepository.findByProcessInstanceIdAndDocumentStateInOrderByUpdatedAtDesc(
                        processInstanceId.trim(), REVIEW_PENDING_STATES);
        documents = documents.stream()
                .filter(document -> hasAreaPermission(document, requester, DocumentPermission.VIEW))
                .toList();

        log.info("document.lifecycle.pending-list tenantId={} processInstanceId={} user={} count={}",
                requester.getTenantId(), processInstanceId, requester.getEmail(), documents.size());
        return documents.stream().map(this::toResponse).toList();
    }

    @Override
    public void onTaskCompleted(Map<String, Object> taskSnapshot, String completedBy) {
        if (taskSnapshot == null || taskSnapshot.isEmpty()) {
            return;
        }

        String processInstanceId = stringValue(taskSnapshot.get("processInstanceId"));
        String taskDefinitionKey = stringValue(taskSnapshot.get("taskDefinitionKey"));
        String taskInstanceId = stringValue(taskSnapshot.get("id"));
        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
        String processKey = extractProcessKey(processDefinitionId);
        Integer processVersion = extractProcessVersion(processDefinitionId);
        if (isBlank(processInstanceId) || isBlank(taskDefinitionKey) || isBlank(processKey) || processVersion == null) {
            return;
        }

        Usuario requester;
        try {
            requester = resolveRequester(completedBy);
        } catch (RuntimeException ex) {
            log.warn("document.lifecycle.task-completed.skip-auth processInstanceId={} taskDefinitionKey={} taskInstanceId={} user={}",
                    processInstanceId, taskDefinitionKey, taskInstanceId, completedBy);
            return;
        }

        TaskDocumentConfig config = taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        effectiveAreaId(requester), processKey, processVersion, taskDefinitionKey)
                .orElse(null);

        List<DocumentMetadata> documents = documentMetadataRepository
                .findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
                        effectiveAreaId(requester), processInstanceId, taskDefinitionKey);
        if (documents.isEmpty()) {
            if (config != null && Boolean.TRUE.equals(config.getRequired())) {
                log.warn("document.lifecycle.required-missing tenantId={} processInstanceId={} processKey={} version={} taskDefinitionKey={} taskInstanceId={} user={}",
                        requester.getTenantId(), processInstanceId, processKey, processVersion, taskDefinitionKey, taskInstanceId, requester.getEmail());
            }
            return;
        }

        Instant now = Instant.now();
        boolean readOnlyAfterComplete = config != null && Boolean.TRUE.equals(config.getReadOnlyAfterComplete());
        for (DocumentMetadata document : documents) {
            if (document.getDocumentState() == null || document.getDocumentState() == DocumentLifecycleState.UPLOADED) {
                document.setDocumentState(DocumentLifecycleState.IN_REVIEW);
            } else if (document.getDocumentState() == DocumentLifecycleState.APPROVED && readOnlyAfterComplete) {
                document.setDocumentState(DocumentLifecycleState.FINAL);
            }
            if (readOnlyAfterComplete) {
                document.setLocked(true);
                document.setLockedBy("workflow");
                document.setLockedAt(now);
            }
            if (isBlank(document.getTaskInstanceId())) {
                document.setTaskInstanceId(taskInstanceId);
            }
            document.setUpdatedAt(now);
            document.setUpdatedBy(requester.getEmail());
        }

        documentMetadataRepository.saveAll(documents);
        log.info("document.lifecycle.task-completed tenantId={} processInstanceId={} processKey={} version={} taskDefinitionKey={} taskInstanceId={} user={} updated={} readOnlyAfterComplete={}",
                requester.getTenantId(), processInstanceId, processKey, processVersion, taskDefinitionKey, taskInstanceId, requester.getEmail(), documents.size(), readOnlyAfterComplete);
    }

    private void ensureEditable(DocumentMetadata metadata, String requesterEmail) {
        if (metadata.getDocumentState() == DocumentLifecycleState.FINAL || metadata.getDocumentState() == DocumentLifecycleState.ARCHIVED) {
            throw new InvalidDocumentAccessException("El documento ya esta en estado final y no puede editarse");
        }
        if (Boolean.TRUE.equals(metadata.getLocked()) && "workflow".equalsIgnoreCase(nullSafe(metadata.getLockedBy()))) {
            throw new InvalidDocumentAccessException("El documento fue bloqueado por avance del workflow");
        }
        if (Boolean.TRUE.equals(metadata.getLocked()) && !requesterEmail.equalsIgnoreCase(nullSafe(metadata.getLockedBy()))) {
            throw new InvalidDocumentAccessException("El documento esta bloqueado por otro usuario");
        }
    }

    private DocumentMetadata findDocument(String documentId, String tenantId) {
        if (isBlank(documentId)) {
            throw new DocumentValidationException("documentId es obligatorio");
        }

        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!tenantId.equals(metadata.getTenantId())) {
            log.warn("document.lifecycle.tenant-forbidden tenantId={} documentTenantId={} documentId={}",
                    tenantId, metadata.getTenantId(), documentId);
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
        DocumentMetadata metadata = documentMetadataRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        if (!hasAreaPermission(metadata, requester, DocumentPermission.VIEW)) {
            log.warn("document.lifecycle.area-forbidden requesterArea={} ownerAreaId={} allowedAreaIds={} documentId={}",
                    requester.getTenantId(), ownerArea(metadata), metadata.getAllowedAreaIds(), documentId);
            throw new DocumentTenantAccessDeniedException();
        }
        return metadata;
    }

    private void enforceAreaPermission(DocumentMetadata metadata, Usuario requester, DocumentPermission permission) {
        if (!hasAreaPermission(metadata, requester, permission)) {
            throw new InvalidDocumentAccessException("No tienes permiso documental para esta accion");
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

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = requesterEmail == null ? null : requesterEmail.trim();
        if (isBlank(normalized)) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(effectiveAreaId(usuario)) && !isAdmin(usuario)) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private void addComment(DocumentMetadata metadata, String user, String comment) {
        if (isBlank(comment)) {
            return;
        }
        List<String> comments = metadata.getComments() == null ? new ArrayList<>() : new ArrayList<>(metadata.getComments());
        comments.add(Instant.now() + " | " + user + " | " + comment.trim());
        metadata.setComments(comments);
    }

    private DocumentMetadataResponseDto toResponse(DocumentMetadata metadata) {
        return DocumentMetadataResponseDto.builder()
                .id(metadata.getId())
                .tenantId(metadata.getTenantId())
                .ownerAreaId(metadata.getOwnerAreaId())
                .allowedAreaIds(metadata.getAllowedAreaIds())
                .accessRules(DocumentResponseMapper.toAccessRuleDtos(metadata.getAccessRules()))
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

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .anyMatch(role -> "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role));
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .anyMatch(role -> "CLIENT".equalsIgnoreCase(role) || "ROLE_CLIENT".equalsIgnoreCase(role));
    }

    private String effectiveAreaId(Usuario usuario) {
        if (!isBlank(usuario.getAreaId())) {
            return usuario.getAreaId().trim();
        }
        return isBlank(usuario.getTenantId()) ? null : usuario.getTenantId().trim();
    }

    private boolean isWorkflowLock(DocumentMetadata metadata) {
        return Boolean.TRUE.equals(metadata.getLocked()) && "workflow".equalsIgnoreCase(nullSafe(metadata.getLockedBy()));
    }

    private String extractProcessKey(String processDefinitionId) {
        if (isBlank(processDefinitionId)) {
            return null;
        }
        int separatorIndex = processDefinitionId.indexOf(':');
        return separatorIndex <= 0 ? processDefinitionId.trim() : processDefinitionId.substring(0, separatorIndex).trim();
    }

    private Integer extractProcessVersion(String processDefinitionId) {
        if (isBlank(processDefinitionId)) {
            return null;
        }
        String[] parts = processDefinitionId.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.valueOf(parts[1].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullSafe(String value) {
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
