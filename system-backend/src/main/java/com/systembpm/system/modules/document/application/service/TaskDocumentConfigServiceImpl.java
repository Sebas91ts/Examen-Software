package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigCreateDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentConfigResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentPermissionsDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationRequestDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentUploadValidationResponseDto;
import com.systembpm.system.modules.document.domain.DocumentRequesterNotFoundException;
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

        String tenantId = requester.getTenantId();
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
        toSave.setRequired(Boolean.TRUE.equals(dto.getRequired()));
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

        String tenantId = requester.getTenantId();
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

        String tenantId = requester.getTenantId();
        String processKey = normalize(dto.getProcessKey());
        Integer version = dto.getProcessVersion();
        String taskDefinitionKey = normalize(dto.getTaskDefinitionKey());

        TaskDocumentConfig config = taskDocumentConfigRepository
                .findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
                        tenantId, processKey, version, taskDefinitionKey
                )
                .orElse(null);

        if (config == null) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(true)
                    .reason("No hay configuracion documental para la tarea")
                    .build();
        }

        if (!Boolean.TRUE.equals(defaultTrue(config.getPermissions() != null ? config.getPermissions().getCanUpload() : null))) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("No tienes permiso para subir documentos en esta tarea")
                    .build();
        }

        if (dto.getSize() != null && config.getMaxFileSizeBytes() != null && config.getMaxFileSizeBytes() > 0 && dto.getSize() > config.getMaxFileSizeBytes()) {
            return TaskDocumentUploadValidationResponseDto.builder()
                    .allowed(false)
                    .reason("El archivo excede el tamano maximo permitido para esta tarea")
                    .build();
        }

        String mimeType = normalizeMime(dto.getMimeType());
        if (!isBlank(mimeType) && config.getAllowedMimeTypes() != null && !config.getAllowedMimeTypes().isEmpty()) {
            boolean allowed = config.getAllowedMimeTypes().stream().anyMatch(rule -> matchesAllowedType(mimeType, rule));
            if (!allowed) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("El tipo MIME no esta permitido para esta tarea")
                        .build();
            }
        }

        if (config.getMaxFiles() != null && config.getMaxFiles() > 0) {
            long count = documentMetadataRepository
                    .findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
                            tenantId,
                            dto.getProcessInstanceId(),
                            taskDefinitionKey
                    )
                    .size();
            if (count >= config.getMaxFiles()) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("Se alcanzo el maximo de archivos permitidos para esta tarea")
                        .build();
            }
        }

        if (Boolean.FALSE.equals(config.getAllowVersioning())) {
            long existingTaskDocuments = documentMetadataRepository
                    .findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
                            tenantId,
                            dto.getProcessInstanceId(),
                            taskDefinitionKey
                    )
                    .size();
            if (existingTaskDocuments > 0) {
                return TaskDocumentUploadValidationResponseDto.builder()
                        .allowed(false)
                        .reason("Esta tarea no permite versionado ni multiples cargas documentales")
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
        if (isBlank(usuario.getTenantId())) {
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
                    .build();
        }
        return TaskDocumentPermissions.builder()
                .canView(defaultTrue(dto.getCanView()))
                .canUpload(defaultTrue(dto.getCanUpload()))
                .canEdit(defaultFalse(dto.getCanEdit()))
                .canDelete(defaultFalse(dto.getCanDelete()))
                .canApprove(defaultFalse(dto.getCanApprove()))
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
                .required(config.getRequired())
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
                        .build())
                .autoGenerateOnTaskStart(config.getAutoGenerateOnTaskStart())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .build();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Boolean defaultTrue(Boolean value) {
        return value == null ? Boolean.TRUE : value;
    }

    private Boolean defaultFalse(Boolean value) {
        return value == null ? Boolean.FALSE : value;
    }
}
