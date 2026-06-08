package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigCreateDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentPermissionsDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentAreaAccessRuleDto;
import com.systembpm.system.modules.document.application.dto.DocumentRequirementDto;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentRequirement;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.domain.TaskDocumentPermissions;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDocumentConfigServiceImpl implements TaskDocumentConfigService {

    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentMetadataRepository documentMetadataRepository;

    @Override
    public TaskDocumentConfigResponseDto save(TaskDocumentConfigCreateDto dto, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        validateDto(dto);

        String tenantId = effectiveAreaId(requester);
        String processKey = normalize(dto.getProcessKey());
        Integer version = dto.getProcessVersion();
        String taskDefinitionKey = normalize(dto.getTaskDefinitionKey());

        TaskDocumentConfig existing = taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        tenantId, processKey, version, taskDefinitionKey
                )
                .orElse(null);

        Instant now = Instant.now();
        TaskDocumentConfig toSave = existing != null ? existing : new TaskDocumentConfig();
        if (toSave.getId() == null) {
            toSave.setCreatedAt(now);
            toSave.setCreatedBy(requester.getEmail());
        }

        toSave.setTenantId(tenantId);
        toSave.setProcessKey(processKey);
        toSave.setProcessVersion(version);
        toSave.setTaskDefinitionKey(taskDefinitionKey);
        List<DocumentRequirement> requirements = normalizeRequirements(dto);
        toSave.setDocumentRequirements(requirements);
        toSave.setDocumentName(normalize(dto.getDocumentName()));
        toSave.setDescription(normalize(dto.getDescription()));
        toSave.setDocumentDirection(normalize(dto.getDocumentDirection()));
        toSave.setRequired(Boolean.TRUE.equals(dto.getRequired()));
        toSave.setAllowMultipleFiles(Boolean.TRUE.equals(dto.getAllowMultipleFiles()));
        toSave.setAllowUpload(defaultTrue(dto.getAllowUpload()));
        toSave.setEditable(dto.getEditable());
        toSave.setAllowEditing(dto.getAllowEditing());
        toSave.setCollaborativeEditing(dto.getCollaborativeEditing());
        toSave.setAllowVersioning(dto.getAllowVersioning());
        toSave.setAllowedMimeTypes(normalizeMimeTypes(dto.getAllowedMimeTypes()));
        toSave.setMaxFileSizeBytes(dto.getMaxFileSizeBytes());
        toSave.setMaxFiles(dto.getMaxFiles());
        toSave.setReadOnlyAfterComplete(dto.getReadOnlyAfterComplete());
        toSave.setRequireApproval(dto.getRequireApproval());
        toSave.setTemplateDocumentId(dto.getTemplateDocumentId());
        toSave.setPermissions(mapPermissions(dto.getPermissions()));
        toSave.setOwnerAreaId(normalize(dto.getOwnerAreaId()));
        toSave.setAllowedAreaIds(normalizeList(dto.getAllowedAreaIds()));
        toSave.setAccessRules(mapAccessRules(dto.getAccessRules()));
        toSave.setShareWithNextArea(dto.getShareWithNextArea());
        toSave.setAutoGenerateOnTaskStart(dto.getAutoGenerateOnTaskStart());
        toSave.setUpdatedAt(now);
        toSave.setUpdatedBy(requester.getEmail());

        TaskDocumentConfig saved = taskDocumentConfigRepository.save(toSave);
        log.info("document.task-config.save tenantId={} processKey={} version={} taskDefinitionKey={} user={}",
                tenantId, processKey, version, taskDefinitionKey, requester.getEmail());
        return toResponse(saved);
    }

    @Override
    public Optional<TaskDocumentConfigResponseDto> get(String processKey, Integer processVersion, String taskDefinitionKey, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        if (isBlank(processKey) || processVersion == null || isBlank(taskDefinitionKey)) {
            throw new DocumentValidationException("processKey, processVersion y taskDefinitionKey son obligatorios");
        }

        String tenantId = effectiveAreaId(requester);
        return taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        tenantId,
                        normalize(processKey),
                        processVersion,
                        normalize(taskDefinitionKey)
                )
                .map(this::toResponse);
    }

    @Override
    public TaskDocumentUploadValidationResponseDto validateUpload(TaskDocumentUploadValidationRequestDto dto, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        validateUploadDto(dto);

        String tenantId = effectiveAreaId(requester);
        String processKey = normalize(dto.getProcessKey());
        Integer version = dto.getProcessVersion();
        String taskDefinitionKey = normalize(dto.getTaskDefinitionKey());

        TaskDocumentConfig config = taskDocumentConfigRepository
                .findFirstByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCaseOrderByUpdatedAtDesc(
                        processKey, version, taskDefinitionKey
                )
                .orElse(null);

        if (config == null) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(true)
                    .reason("No hay configuracion documental para la tarea")
                    .build();
        }

        DocumentRequirement requirement = selectRequirement(config, dto.getDocumentRequirementId(), normalizeMime(dto.getMimeType()), dto.getSize());
        if (requirement == null) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(true)
                    .reason("La tarea no tiene requerimientos documentales activos")
                    .build();
        }

        if (!Boolean.TRUE.equals(defaultTrue(config.getPermissions() != null ? config.getPermissions().getCanUpload() : null))
                || !canRequirement(requirement, requester, DocumentPermission.UPLOAD)) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("No tienes permiso para subir documentos en esta tarea")
                    .build();
        }

        if (Boolean.FALSE.equals(requirement.getAllowUpload())) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("Esta tarea no permite subir documentos")
                    .build();
        }

        if (dto.getSize() != null && requirement.getMaxFileSizeBytes() != null && requirement.getMaxFileSizeBytes() > 0 && dto.getSize() > requirement.getMaxFileSizeBytes()) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("El archivo excede el tamano maximo permitido para esta tarea")
                    .build();
        }

        String mimeType = normalizeMime(dto.getMimeType());
        if (!isBlank(mimeType) && requirement.getAllowedMimeTypes() != null && !requirement.getAllowedMimeTypes().isEmpty()) {
            boolean allowed = requirement.getAllowedMimeTypes().stream().anyMatch(rule -> matchesAllowedType(mimeType, rule));
            if (!allowed) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("El tipo MIME no esta permitido para esta tarea")
                        .build();
            }
        }

        long existingRequirementDocuments = countRequirementDocuments(dto.getProcessInstanceId(), requirement);
        if (requirement.getMaxFiles() != null && requirement.getMaxFiles() > 0) {
            long count = existingRequirementDocuments;
            if (count >= requirement.getMaxFiles()) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("Se alcanzo el maximo de archivos permitidos para esta tarea")
                        .build();
            }
        }

        if (Boolean.FALSE.equals(config.getAllowVersioning()) || Boolean.FALSE.equals(requirement.getAllowMultipleFiles())) {
            if (existingRequirementDocuments > 0) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("Este documento ya fue adjuntado y la tarea no permite multiples archivos")
                        .build();
            }
        }

        boolean workflowLocked = documentMetadataRepository
                .findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
                        tenantId,
                        dto.getProcessInstanceId(),
                        taskDefinitionKey
                )
                .stream()
                .anyMatch(document -> Boolean.TRUE.equals(document.getLocked())
                        && "workflow".equalsIgnoreCase(document.getLockedBy()));
        if (workflowLocked) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("La tarea ya avanzo en el workflow y sus documentos estan en solo lectura")
                    .build();
        }

        return TaskDocumentUploadValidationResponseDto.builder()
                .allowed(true)
                .reason("OK")
                .build();
    }

    private long countRequirementDocuments(String processInstanceId, DocumentRequirement requirement) {
        if (isBlank(processInstanceId)) {
            return 0;
        }
        return documentMetadataRepository.findByProcessInstanceIdOrderByUploadedAtDesc(processInstanceId)
                .stream()
                .filter(document -> belongsToRequirementSlot(document, requirement))
                .count();
    }

    private boolean belongsToRequirementSlot(DocumentMetadata document, DocumentRequirement requirement) {
        return !isBlank(requirement.getId()) && requirement.getId().equals(document.getDocumentRequirementId())
                || matchesRequirementName(document, requirement);
    }

    private boolean matchesRequirementName(DocumentMetadata document, DocumentRequirement requirement) {
        String expectedName = normalizeComparable(requirement.getName());
        if (isBlank(expectedName)) {
            return false;
        }
        return expectedName.equals(normalizeComparable(document.getDocumentRequirementName()))
                || expectedName.equals(normalizeComparable(stripExtension(document.getOriginalName())))
                || expectedName.equals(normalizeComparable(document.getOriginalName()))
                || expectedName.equals(normalizeComparable(document.getFileName()));
    }

    private String stripExtension(String fileName) {
        String normalized = normalize(fileName);
        if (normalized == null) {
            return null;
        }
        int index = normalized.lastIndexOf('.');
        return index <= 0 ? normalized : normalized.substring(0, index);
    }

    private void validateDto(TaskDocumentConfigCreateDto dto) {
        if (dto == null) {
            throw new DocumentValidationException("La configuracion documental es obligatoria");
        }
        if (isBlank(dto.getProcessKey())) {
            throw new DocumentValidationException("processKey es obligatorio");
        }
        if (dto.getProcessVersion() == null || dto.getProcessVersion() < 1) {
            throw new DocumentValidationException("processVersion es obligatorio");
        }
        if (isBlank(dto.getTaskDefinitionKey())) {
            throw new DocumentValidationException("taskDefinitionKey es obligatorio");
        }
        if (dto.getMaxFiles() != null && dto.getMaxFiles() < 0) {
            throw new DocumentValidationException("maxFiles no puede ser negativo");
        }
        if (dto.getMaxFileSizeBytes() != null && dto.getMaxFileSizeBytes() < 0) {
            throw new DocumentValidationException("maxFileSizeBytes no puede ser negativo");
        }
        for (DocumentRequirementDto requirement : dto.getDocumentRequirements() == null ? List.<DocumentRequirementDto>of() : dto.getDocumentRequirements()) {
            if (requirement.getMaxFiles() != null && requirement.getMaxFiles() < 0) {
                throw new DocumentValidationException("maxFiles no puede ser negativo");
            }
            if (requirement.getMaxFileSizeBytes() != null && requirement.getMaxFileSizeBytes() < 0) {
                throw new DocumentValidationException("maxFileSizeBytes no puede ser negativo");
            }
        }
    }

    private void validateUploadDto(TaskDocumentUploadValidationRequestDto dto) {
        if (dto == null) {
            throw new DocumentValidationException("La solicitud es obligatoria");
        }
        if (isBlank(dto.getProcessKey()) || dto.getProcessVersion() == null || isBlank(dto.getTaskDefinitionKey()) || isBlank(dto.getProcessInstanceId())) {
            throw new DocumentValidationException("processKey, processVersion, taskDefinitionKey y processInstanceId son obligatorios");
        }
        if (dto.getSize() != null && dto.getSize() < 0) {
            throw new DocumentValidationException("size no puede ser negativo");
        }
    }

    private Usuario resolveRequester(String requesterEmail) {
        String normalized = normalize(requesterEmail);
        if (normalized == null) {
            throw new DocumentRequesterNotFoundException();
        }
        Usuario usuario = usuarioRepository.findByEmail(normalized)
                .orElseThrow(DocumentRequesterNotFoundException::new);
        if (isBlank(effectiveAreaId(usuario)) && !isAdmin(usuario)) {
            throw new DocumentTenantAccessDeniedException();
        }
        return usuario;
    }

    private TaskDocumentPermissions mapPermissions(TaskDocumentPermissionsDto dto) {
        if (dto == null) {
            return TaskDocumentPermissions.builder()
                    .canView(true)
                    .canUpload(true)
                    .canEdit(false)
                    .canDelete(false)
                    .canApprove(false)
                    .canDownload(true)
                    .canReject(false)
                    .canLock(false)
                    .build();
        }
        return TaskDocumentPermissions.builder()
                .canView(defaultTrue(dto.getCanView()))
                .canUpload(defaultTrue(dto.getCanUpload()))
                .canEdit(defaultFalse(dto.getCanEdit()))
                .canDelete(defaultFalse(dto.getCanDelete()))
                .canApprove(defaultFalse(dto.getCanApprove()))
                .canDownload(defaultTrue(dto.getCanDownload()))
                .canReject(defaultFalse(dto.getCanReject()))
                .canLock(defaultFalse(dto.getCanLock()))
                .build();
    }

    private TaskDocumentConfigResponseDto toResponse(TaskDocumentConfig config) {
        TaskDocumentPermissions permissions = config.getPermissions();
        return TaskDocumentConfigResponseDto.builder()
                .id(config.getId())
                .tenantId(config.getTenantId())
                .processKey(config.getProcessKey())
                .processVersion(config.getProcessVersion())
                .taskDefinitionKey(config.getTaskDefinitionKey())
                .documentRequirements(toRequirementDtos(resolveRequirements(config)))
                .documentName(config.getDocumentName())
                .description(config.getDescription())
                .documentDirection(config.getDocumentDirection())
                .required(config.getRequired())
                .allowMultipleFiles(config.getAllowMultipleFiles())
                .allowUpload(config.getAllowUpload())
                .editable(config.getEditable())
                .allowEditing(config.getAllowEditing())
                .collaborativeEditing(config.getCollaborativeEditing())
                .allowVersioning(config.getAllowVersioning())
                .allowedMimeTypes(config.getAllowedMimeTypes())
                .maxFileSizeBytes(config.getMaxFileSizeBytes())
                .maxFiles(config.getMaxFiles())
                .readOnlyAfterComplete(config.getReadOnlyAfterComplete())
                .requireApproval(config.getRequireApproval())
                .templateDocumentId(config.getTemplateDocumentId())
                .permissions(permissions == null ? null : TaskDocumentPermissionsDto.builder()
                        .canView(permissions.getCanView())
                        .canUpload(permissions.getCanUpload())
                        .canEdit(permissions.getCanEdit())
                        .canDelete(permissions.getCanDelete())
                        .canApprove(permissions.getCanApprove())
                        .canDownload(permissions.getCanDownload())
                        .canReject(permissions.getCanReject())
                        .canLock(permissions.getCanLock())
                        .build())
                .ownerAreaId(config.getOwnerAreaId())
                .allowedAreaIds(config.getAllowedAreaIds())
                .accessRules(toAccessRuleDtos(config.getAccessRules()))
                .shareWithNextArea(config.getShareWithNextArea())
                .autoGenerateOnTaskStart(config.getAutoGenerateOnTaskStart())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .build();
    }

    private List<DocumentRequirement> normalizeRequirements(TaskDocumentConfigCreateDto dto) {
        if (dto.getDocumentRequirements() == null || dto.getDocumentRequirements().isEmpty()) {
            return hasLegacyDocumentFields(dto) ? List.of(legacyRequirement(dto)) : List.of();
        }
        return dto.getDocumentRequirements().stream()
                .filter(requirement -> requirement != null)
                .map(this::mapRequirement)
                .toList();
    }

    private boolean hasLegacyDocumentFields(TaskDocumentConfigCreateDto dto) {
        return !isBlank(dto.getDocumentName())
                || !isBlank(dto.getDescription())
                || Boolean.TRUE.equals(dto.getRequired())
                || Boolean.TRUE.equals(dto.getEditable())
                || Boolean.TRUE.equals(dto.getRequireApproval())
                || (dto.getAllowedMimeTypes() != null && !dto.getAllowedMimeTypes().isEmpty())
                || (dto.getAccessRules() != null && !dto.getAccessRules().isEmpty());
    }

    private DocumentRequirement mapRequirement(DocumentRequirementDto dto) {
        String id = normalize(dto.getId());
        String direction = normalizeDirection(dto.getDocumentDirection());
        return DocumentRequirement.builder()
                .id(isBlank(id) ? UUID.randomUUID().toString() : id)
                .name(normalize(dto.getName()))
                .description(normalize(dto.getDescription()))
                .documentDirection(direction)
                .required(Boolean.TRUE.equals(dto.getRequired()))
                .allowUpload(defaultTrue(dto.getAllowUpload()))
                .allowMultipleFiles(Boolean.TRUE.equals(dto.getAllowMultipleFiles()))
                .editable(defaultFalse(dto.getEditable()))
                .collaborativeEditing(defaultFalse(dto.getCollaborativeEditing()))
                .requireApproval(defaultFalse(dto.getRequireApproval()))
                .readOnlyAfterComplete(defaultFalse(dto.getReadOnlyAfterComplete()))
                .allowedMimeTypes(normalizeMimeTypes(dto.getAllowedMimeTypes()))
                .maxFileSizeBytes(dto.getMaxFileSizeBytes())
                .maxFiles(dto.getMaxFiles())
                .ownerAreaId(normalize(dto.getOwnerAreaId()))
                .allowedAreaIds(normalizeList(dto.getAllowedAreaIds()))
                .accessRules(mapAccessRules(dto.getAccessRules()))
                .documentLifecyclePolicy(normalizeLifecyclePolicy(dto.getDocumentLifecyclePolicy(), direction))
                .build();
    }

    private DocumentRequirement legacyRequirement(TaskDocumentConfigCreateDto dto) {
        String direction = normalizeDirection(dto.getDocumentDirection());
        return DocumentRequirement.builder()
                .id(UUID.randomUUID().toString())
                .name(normalize(dto.getDocumentName()))
                .description(normalize(dto.getDescription()))
                .documentDirection(direction)
                .required(Boolean.TRUE.equals(dto.getRequired()))
                .allowUpload(defaultTrue(dto.getAllowUpload()))
                .allowMultipleFiles(Boolean.TRUE.equals(dto.getAllowMultipleFiles()))
                .editable(defaultFalse(dto.getEditable()))
                .collaborativeEditing(defaultFalse(dto.getCollaborativeEditing()))
                .requireApproval(defaultFalse(dto.getRequireApproval()))
                .readOnlyAfterComplete(defaultFalse(dto.getReadOnlyAfterComplete()))
                .allowedMimeTypes(normalizeMimeTypes(dto.getAllowedMimeTypes()))
                .maxFileSizeBytes(dto.getMaxFileSizeBytes())
                .maxFiles(dto.getMaxFiles())
                .ownerAreaId(normalize(dto.getOwnerAreaId()))
                .allowedAreaIds(normalizeList(dto.getAllowedAreaIds()))
                .accessRules(mapAccessRules(dto.getAccessRules()))
                .documentLifecyclePolicy(defaultLifecyclePolicy(direction))
                .build();
    }

    private List<DocumentRequirement> resolveRequirements(TaskDocumentConfig config) {
        if (config.getDocumentRequirements() != null && !config.getDocumentRequirements().isEmpty()) {
            return config.getDocumentRequirements();
        }
        if (!hasLegacyDocumentFields(config)) {
            return List.of();
        }
        return List.of(DocumentRequirement.builder()
                .id("legacy-main-document")
                .name(config.getDocumentName())
                .description(config.getDescription())
                .documentDirection(normalizeDirection(config.getDocumentDirection()))
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

    private boolean hasLegacyDocumentFields(TaskDocumentConfig config) {
        return !isBlank(config.getDocumentName())
                || !isBlank(config.getDescription())
                || Boolean.TRUE.equals(config.getRequired())
                || Boolean.TRUE.equals(config.getEditable())
                || Boolean.TRUE.equals(config.getRequireApproval())
                || (config.getAllowedMimeTypes() != null && !config.getAllowedMimeTypes().isEmpty())
                || (config.getAccessRules() != null && !config.getAccessRules().isEmpty());
    }

    private List<DocumentRequirementDto> toRequirementDtos(List<DocumentRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        return requirements.stream()
                .map(requirement -> DocumentRequirementDto.builder()
                        .id(requirement.getId())
                        .name(requirement.getName())
                        .description(requirement.getDescription())
                        .documentDirection(normalizeDirection(requirement.getDocumentDirection()))
                        .required(requirement.getRequired())
                        .allowUpload(requirement.getAllowUpload())
                        .allowMultipleFiles(requirement.getAllowMultipleFiles())
                        .editable(requirement.getEditable())
                        .collaborativeEditing(requirement.getCollaborativeEditing())
                        .requireApproval(requirement.getRequireApproval())
                        .readOnlyAfterComplete(requirement.getReadOnlyAfterComplete())
                        .allowedMimeTypes(requirement.getAllowedMimeTypes())
                        .maxFileSizeBytes(requirement.getMaxFileSizeBytes())
                        .maxFiles(requirement.getMaxFiles())
                        .ownerAreaId(requirement.getOwnerAreaId())
                        .allowedAreaIds(requirement.getAllowedAreaIds())
                        .accessRules(toAccessRuleDtos(requirement.getAccessRules()))
                        .documentLifecyclePolicy(normalizeLifecyclePolicy(requirement.getDocumentLifecyclePolicy()))
                        .build())
                .toList();
    }

    private DocumentRequirement selectRequirement(TaskDocumentConfig config, String requirementId, String mimeType, Long size) {
        List<DocumentRequirement> requirements = resolveRequirements(config);
        if (requirements.isEmpty()) {
            return null;
        }
        if (!isBlank(requirementId)) {
            return requirements.stream()
                    .filter(requirement -> requirementId.trim().equals(requirement.getId()))
                    .findFirst()
                    .orElseThrow(() -> new DocumentValidationException("documentRequirementId no existe para esta tarea"));
        }
        return requirements.stream()
                .filter(requirement -> Boolean.TRUE.equals(defaultTrue(requirement.getAllowUpload())))
                .filter(requirement -> isBlank(mimeType) || requirement.getAllowedMimeTypes() == null || requirement.getAllowedMimeTypes().isEmpty()
                        || requirement.getAllowedMimeTypes().stream().anyMatch(rule -> matchesAllowedType(mimeType, rule)))
                .filter(requirement -> size == null || requirement.getMaxFileSizeBytes() == null || requirement.getMaxFileSizeBytes() <= 0
                        || size <= requirement.getMaxFileSizeBytes())
                .findFirst()
                .orElse(requirements.get(0));
    }

    private boolean canRequirement(DocumentRequirement requirement, Usuario requester, DocumentPermission permission) {
        if (isAdmin(requester)) {
            return true;
        }
        if (isClient(requester)) {
            return permission == DocumentPermission.VIEW || permission == DocumentPermission.DOWNLOAD;
        }
        String areaId = effectiveAreaId(requester);
        if (isBlank(areaId)) {
            return false;
        }
        if (!hasExplicitAreaRestrictions(requirement)) {
            return true;
        }
        if (areaId.equals(requirement.getOwnerAreaId())) {
            return true;
        }
        DocumentAreaAccessRule rule = findRule(requirement.getAccessRules(), areaId);
        if (rule != null) {
            return switch (permission) {
                case VIEW -> Boolean.TRUE.equals(rule.getCanView());
                case UPLOAD -> Boolean.TRUE.equals(rule.getCanUpload());
                case EDIT -> Boolean.TRUE.equals(rule.getCanEdit());
                case DOWNLOAD -> Boolean.TRUE.equals(rule.getCanDownload()) || Boolean.TRUE.equals(rule.getCanView());
                case APPROVE -> Boolean.TRUE.equals(rule.getCanApprove());
                case REJECT -> Boolean.TRUE.equals(rule.getCanReject());
                case LOCK -> Boolean.TRUE.equals(rule.getCanLock());
            };
        }
        return (permission == DocumentPermission.VIEW || permission == DocumentPermission.DOWNLOAD)
                && requirement.getAllowedAreaIds() != null
                && requirement.getAllowedAreaIds().contains(areaId);
    }

    private DocumentAreaAccessRule findRule(List<DocumentAreaAccessRule> rules, String areaId) {
        if (rules == null || areaId == null) {
            return null;
        }
        return rules.stream()
                .filter(rule -> rule != null && areaId.equals(rule.getAreaId()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasExplicitAreaRestrictions(DocumentRequirement requirement) {
        return !isBlank(requirement.getOwnerAreaId())
                || (requirement.getAllowedAreaIds() != null && !requirement.getAllowedAreaIds().isEmpty())
                || (requirement.getAccessRules() != null && !requirement.getAccessRules().isEmpty());
    }

    private boolean isAdmin(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
    }

    private boolean isClient(Usuario usuario) {
        return usuario.getRoles() != null && usuario.getRoles().stream()
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private String effectiveAreaId(Usuario usuario) {
        if (!isBlank(usuario.getAreaId())) {
            return usuario.getAreaId().trim();
        }
        return isBlank(usuario.getTenantId()) ? null : usuario.getTenantId().trim();
    }

    private String normalizeDirection(String value) {
        String normalized = normalize(value);
        if ("OUTPUT".equalsIgnoreCase(normalized)) {
            return "OUTPUT";
        }
        return "INPUT";
    }

    private String normalizeLifecyclePolicy(String value) {
        String normalized = normalize(value);
        if ("AVAILABLE_FOR_NEXT_TASKS".equalsIgnoreCase(normalized)) {
            return "AVAILABLE_FOR_NEXT_TASKS";
        }
        if ("AVAILABLE_FOR_INSTANCE".equalsIgnoreCase(normalized)) {
            return "AVAILABLE_FOR_INSTANCE";
        }
        if ("PUBLISH_ON_PROCESS_END".equalsIgnoreCase(normalized)) {
            return "PUBLISH_ON_PROCESS_END";
        }
        return "TASK_ONLY";
    }

    private String normalizeLifecyclePolicy(String value, String documentDirection) {
        String normalized = normalize(value);
        if (isBlank(normalized)) {
            return defaultLifecyclePolicy(documentDirection);
        }
        return normalizeLifecyclePolicy(normalized);
    }

    private String defaultLifecyclePolicy(String documentDirection) {
        return "OUTPUT".equalsIgnoreCase(normalizeDirection(documentDirection))
                ? "AVAILABLE_FOR_NEXT_TASKS"
                : "TASK_ONLY";
    }

    private List<String> normalizeMimeTypes(List<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return List.of();
        }
        return allowed.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<DocumentAreaAccessRule> mapAccessRules(List<DocumentAreaAccessRuleDto> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .filter(rule -> rule != null && !isBlank(rule.getAreaId()))
                .map(rule -> DocumentAreaAccessRule.builder()
                        .areaId(normalize(rule.getAreaId()))
                        .canView(defaultFalse(rule.getCanView()))
                        .canUpload(defaultFalse(rule.getCanUpload()))
                        .canEdit(defaultFalse(rule.getCanEdit()))
                        .canDownload(defaultFalse(rule.getCanDownload()))
                        .canApprove(defaultFalse(rule.getCanApprove()))
                        .canReject(defaultFalse(rule.getCanReject()))
                        .canLock(defaultFalse(rule.getCanLock()))
                        .build())
                .toList();
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

    private String normalizeMime(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
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

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeComparable(String value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Boolean defaultTrue(Boolean value) {
        return value == null ? Boolean.TRUE : value;
    }

    private Boolean defaultFalse(Boolean value) {
        return value == null ? Boolean.FALSE : value;
    }

    private enum DocumentPermission {
        VIEW,
        UPLOAD,
        EDIT,
        DOWNLOAD,
        APPROVE,
        REJECT,
        LOCK
    }
}
