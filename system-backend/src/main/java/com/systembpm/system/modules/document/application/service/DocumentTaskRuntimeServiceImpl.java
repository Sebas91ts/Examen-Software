package com.systembpm.system.modules.document.application.service;

import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentRuntimeRequirementDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentRuntimeResponseDto;
import com.systembpm.system.modules.document.application.dto.TaskDocumentRuntimeSummaryDto;
import com.systembpm.system.modules.document.domain.DocumentAreaAccessRule;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentRequirement;
import com.systembpm.system.modules.document.domain.DocumentTenantAccessDeniedException;
import com.systembpm.system.modules.document.domain.DocumentValidationException;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.infrastructure.repository.DocumentMetadataRepository;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTaskRuntimeServiceImpl implements DocumentTaskRuntimeService {

    private static final String TASK_ONLY = "TASK_ONLY";
    private static final String AVAILABLE_FOR_NEXT_TASKS = "AVAILABLE_FOR_NEXT_TASKS";
    private static final String AVAILABLE_FOR_INSTANCE = "AVAILABLE_FOR_INSTANCE";
    private static final String PUBLISH_ON_PROCESS_END = "PUBLISH_ON_PROCESS_END";

    private final TaskDocumentConfigRepository taskDocumentConfigRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentResponseMapper documentResponseMapper;

    @Override
    public TaskDocumentRuntimeResponseDto getRuntime(Map<String, Object> taskSnapshot, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        TaskContext context = resolveContext(taskSnapshot);
        if (isBlank(context.processKey()) || context.processVersion() == null || isBlank(context.taskDefinitionKey())) {
            return emptyRuntime(context);
        }

        TaskDocumentConfig config = resolveTaskConfig(context);
        List<DocumentRequirement> requirements = resolveRequirements(config);
        if (requirements.isEmpty()) {
            return emptyRuntime(context);
        }

        List<DocumentMetadata> instanceDocuments = documentMetadataRepository
                .findByProcessInstanceIdOrderByUploadedAtDesc(context.processInstanceId());

        List<TaskDocumentRuntimeRequirementDto> runtimeRequirements = requirements.stream()
                .map(requirement -> toRuntimeRequirement(requirement, instanceDocuments, requester, context))
                .toList();

        TaskDocumentRuntimeSummaryDto summary = buildSummary(runtimeRequirements);
        log.info("document.runtime.load processInstanceId={} processKey={} version={} taskDefinitionKey={} taskId={} user={} total={} missingRequired={}",
                context.processInstanceId(), context.processKey(), context.processVersion(), context.taskDefinitionKey(), context.taskInstanceId(),
                requester.getEmail(), summary.getTotal(), summary.getMissingRequired());

        return TaskDocumentRuntimeResponseDto.builder()
                .processKey(context.processKey())
                .processVersion(context.processVersion())
                .processInstanceId(context.processInstanceId())
                .taskDefinitionKey(context.taskDefinitionKey())
                .taskInstanceId(context.taskInstanceId())
                .requirements(runtimeRequirements)
                .summary(summary)
                .build();
    }

    @Override
    public void validateBeforeComplete(Map<String, Object> taskSnapshot, String requesterEmail) {
        TaskDocumentRuntimeResponseDto runtime = getRuntime(taskSnapshot, requesterEmail);
        List<String> missing = runtime.getRequirements() == null ? List.of() : runtime.getRequirements().stream()
                .filter(requirement -> Boolean.TRUE.equals(requirement.getRequired()))
                .filter(requirement -> "MISSING".equals(requirement.getStatus()))
                .map(TaskDocumentRuntimeRequirementDto::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        if (!missing.isEmpty()) {
            throw new DocumentValidationException("No puedes completar la tarea. Falta adjuntar: " + String.join(", ", missing) + ".");
        }
    }

    private TaskDocumentRuntimeRequirementDto toRuntimeRequirement(
            DocumentRequirement requirement,
            List<DocumentMetadata> instanceDocuments,
            Usuario requester,
            TaskContext context
    ) {
        List<DocumentMetadataResponseDto> documents = instanceDocuments.stream()
                .filter(document -> belongsToRequirement(document, requirement, context))
                .filter(document -> canDocument(document, requester, RuntimePermission.VIEW)
                        || canRequirement(requirement, requester, RuntimePermission.VIEW))
                .map(documentResponseMapper::toMetadataResponse)
                .toList();
        String status = resolveStatus(requirement, documents);

        boolean canView = canRequirement(requirement, requester, RuntimePermission.VIEW);
        boolean canUpload = Boolean.TRUE.equals(defaultTrue(requirement.getAllowUpload()))
                && canRequirement(requirement, requester, RuntimePermission.UPLOAD)
                && hasAvailableUploadSlot(requirement, documents);
        boolean canEdit = Boolean.TRUE.equals(requirement.getEditable())
                && canRequirement(requirement, requester, RuntimePermission.EDIT);

        return TaskDocumentRuntimeRequirementDto.builder()
                .id(requirement.getId())
                .name(defaultName(requirement))
                .description(requirement.getDescription())
                .documentDirection(normalizeDirection(requirement.getDocumentDirection()))
                .documentLifecyclePolicy(normalizeLifecyclePolicy(requirement.getDocumentLifecyclePolicy()))
                .required(Boolean.TRUE.equals(requirement.getRequired()))
                .allowUpload(Boolean.TRUE.equals(defaultTrue(requirement.getAllowUpload())))
                .allowMultipleFiles(Boolean.TRUE.equals(requirement.getAllowMultipleFiles()))
                .editable(Boolean.TRUE.equals(requirement.getEditable()))
                .collaborativeEditing(Boolean.TRUE.equals(requirement.getCollaborativeEditing()))
                .requireApproval(Boolean.TRUE.equals(requirement.getRequireApproval()))
                .readOnlyAfterComplete(Boolean.TRUE.equals(requirement.getReadOnlyAfterComplete()))
                .allowedMimeTypes(requirement.getAllowedMimeTypes() == null ? List.of() : requirement.getAllowedMimeTypes())
                .maxFileSizeBytes(requirement.getMaxFileSizeBytes())
                .maxFiles(requirement.getMaxFiles())
                .status(status)
                .message(resolveMessage(requirement, status))
                .canView(canView)
                .canUpload(canUpload)
                .canEdit(canEdit)
                .canDownload(canRequirement(requirement, requester, RuntimePermission.DOWNLOAD))
                .canApprove(canRequirement(requirement, requester, RuntimePermission.APPROVE))
                .canReject(canRequirement(requirement, requester, RuntimePermission.REJECT))
                .canLock(canRequirement(requirement, requester, RuntimePermission.LOCK))
                .documents(documents)
                .build();
    }

    private boolean belongsToRequirement(DocumentMetadata document, DocumentRequirement requirement, TaskContext context) {
        String policy = normalizeLifecyclePolicy(coalesce(document.getDocumentLifecyclePolicy(), requirement.getDocumentLifecyclePolicy()));
        boolean sameRequirement = !isBlank(requirement.getId()) && requirement.getId().equals(document.getDocumentRequirementId());
        boolean sameName = matchesRequirementName(document, requirement);
        boolean sameTask = context.taskDefinitionKey().equalsIgnoreCase(nullToEmpty(document.getTaskDefinitionKey()));
        boolean currentTaskConsumesDocument = "INPUT".equals(normalizeDirection(requirement.getDocumentDirection()));
        boolean previousTaskGeneratedDocument = "OUTPUT".equals(normalizeDirection(document.getDocumentDirection()));

        if (TASK_ONLY.equals(policy)) {
            if (currentTaskConsumesDocument && previousTaskGeneratedDocument && sameName) {
                return true;
            }
            return sameTask && (sameRequirement || sameName);
        }
        if (AVAILABLE_FOR_NEXT_TASKS.equals(policy) || AVAILABLE_FOR_INSTANCE.equals(policy) || PUBLISH_ON_PROCESS_END.equals(policy)) {
            return sameRequirement || sameName;
        }
        return sameTask && (sameRequirement || sameName);
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

    private boolean hasAvailableUploadSlot(DocumentRequirement requirement, List<DocumentMetadataResponseDto> documents) {
        int currentCount = documents == null ? 0 : documents.size();
        if (currentCount <= 0) {
            return true;
        }
        if (!Boolean.TRUE.equals(requirement.getAllowMultipleFiles())) {
            return false;
        }
        Integer maxFiles = requirement.getMaxFiles();
        return maxFiles == null || maxFiles <= 0 || currentCount < maxFiles;
    }

    private String stripExtension(String fileName) {
        String normalized = normalize(fileName);
        if (normalized == null) {
            return null;
        }
        int index = normalized.lastIndexOf('.');
        return index <= 0 ? normalized : normalized.substring(0, index);
    }

    private String resolveStatus(DocumentRequirement requirement, List<DocumentMetadataResponseDto> documents) {
        if (documents == null || documents.isEmpty()) {
            return Boolean.TRUE.equals(requirement.getRequired()) ? "MISSING" : "PENDING";
        }
        boolean rejected = documents.stream().anyMatch(document -> document.getDocumentState() == DocumentLifecycleState.REJECTED);
        if (rejected) {
            return "REJECTED";
        }
        boolean approved = documents.stream().anyMatch(document -> document.getDocumentState() == DocumentLifecycleState.APPROVED);
        if (approved) {
            return "APPROVED";
        }
        boolean inReview = documents.stream().anyMatch(document -> document.getDocumentState() == DocumentLifecycleState.IN_REVIEW);
        if (inReview || Boolean.TRUE.equals(requirement.getRequireApproval())) {
            return "IN_REVIEW";
        }
        return "COMPLETED";
    }

    private String resolveMessage(DocumentRequirement requirement, String status) {
        if ("MISSING".equals(status)) {
            return "Este documento es obligatorio.";
        }
        if ("PENDING".equals(status)) {
            return "Documento pendiente.";
        }
        if ("IN_REVIEW".equals(status)) {
            return "Documento en revision.";
        }
        if ("APPROVED".equals(status)) {
            return "Documento aprobado.";
        }
        if ("REJECTED".equals(status)) {
            return "Documento rechazado.";
        }
        return Boolean.TRUE.equals(requirement.getEditable())
                ? "Documento listo para editar o consultar."
                : "Documento cargado.";
    }

    private TaskDocumentRuntimeSummaryDto buildSummary(List<TaskDocumentRuntimeRequirementDto> requirements) {
        int completed = 0;
        int missing = 0;
        int editable = 0;
        int pendingReview = 0;
        for (TaskDocumentRuntimeRequirementDto requirement : requirements) {
            if ("MISSING".equals(requirement.getStatus())) {
                missing++;
            }
            if ("COMPLETED".equals(requirement.getStatus()) || "APPROVED".equals(requirement.getStatus())) {
                completed++;
            }
            if (Boolean.TRUE.equals(requirement.getEditable())) {
                editable++;
            }
            if ("IN_REVIEW".equals(requirement.getStatus())) {
                pendingReview++;
            }
        }
        return TaskDocumentRuntimeSummaryDto.builder()
                .total(requirements.size())
                .completed(completed)
                .missingRequired(missing)
                .editable(editable)
                .pendingReview(pendingReview)
                .build();
    }

    private TaskDocumentConfig resolveTaskConfig(TaskContext context) {
        return taskDocumentConfigRepository
                .findFirstByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCaseOrderByUpdatedAtDesc(
                        context.processKey(), context.processVersion(), context.taskDefinitionKey())
                .orElse(null);
    }

    private List<DocumentRequirement> resolveRequirements(TaskDocumentConfig config) {
        if (config == null) {
            return List.of();
        }
        if (config.getDocumentRequirements() != null && !config.getDocumentRequirements().isEmpty()) {
            return config.getDocumentRequirements();
        }
        if (isBlank(config.getDocumentName()) && !Boolean.TRUE.equals(config.getRequired()) && !Boolean.TRUE.equals(config.getEditable())) {
            return List.of();
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

    private boolean canRequirement(DocumentRequirement requirement, Usuario requester, RuntimePermission permission) {
        if (isAdmin(requester)) {
            return true;
        }
        if (isClient(requester)) {
            return permission == RuntimePermission.VIEW || permission == RuntimePermission.DOWNLOAD;
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
        return (permission == RuntimePermission.VIEW || permission == RuntimePermission.DOWNLOAD)
                && requirement.getAllowedAreaIds() != null
                && requirement.getAllowedAreaIds().contains(areaId);
    }

    private boolean canDocument(DocumentMetadata document, Usuario requester, RuntimePermission permission) {
        if (isAdmin(requester)) {
            return true;
        }
        if (isClient(requester)) {
            return requester.getEmail() != null && requester.getEmail().equalsIgnoreCase(document.getUploadedBy())
                    && (permission == RuntimePermission.VIEW || permission == RuntimePermission.DOWNLOAD);
        }
        String areaId = effectiveAreaId(requester);
        if (isBlank(areaId)) {
            return false;
        }
        if (areaId.equals(ownerArea(document)) || areaId.equals(document.getTenantId())) {
            return true;
        }
        DocumentAreaAccessRule rule = findRule(document.getAccessRules(), areaId);
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
        return (permission == RuntimePermission.VIEW || permission == RuntimePermission.DOWNLOAD)
                && document.getAllowedAreaIds() != null
                && document.getAllowedAreaIds().contains(areaId);
    }

    private Usuario resolveRequester(String requesterEmail) {
        String email = normalize(requesterEmail);
        if (email == null) {
            throw new DocumentTenantAccessDeniedException();
        }
        Usuario requester = usuarioRepository.findByEmail(email)
                .orElseThrow(DocumentTenantAccessDeniedException::new);
        if (isBlank(effectiveAreaId(requester)) && !isAdmin(requester)) {
            throw new DocumentTenantAccessDeniedException();
        }
        return requester;
    }

    private TaskContext resolveContext(Map<String, Object> taskSnapshot) {
        String processDefinitionId = stringValue(taskSnapshot.get("processDefinitionId"));
        String processKey = firstText(stringValue(taskSnapshot.get("processKey")), extractProcessKey(processDefinitionId));
        Integer processVersion = intValue(taskSnapshot.get("processVersion"));
        if (processVersion == null) {
            processVersion = extractProcessVersion(processDefinitionId);
        }
        return new TaskContext(
                processKey,
                processVersion,
                stringValue(taskSnapshot.get("processInstanceId")),
                stringValue(taskSnapshot.get("taskDefinitionKey")),
                stringValue(taskSnapshot.get("id"))
        );
    }

    private TaskDocumentRuntimeResponseDto emptyRuntime(TaskContext context) {
        return TaskDocumentRuntimeResponseDto.builder()
                .processKey(context.processKey())
                .processVersion(context.processVersion())
                .processInstanceId(context.processInstanceId())
                .taskDefinitionKey(context.taskDefinitionKey())
                .taskInstanceId(context.taskInstanceId())
                .requirements(List.of())
                .summary(TaskDocumentRuntimeSummaryDto.builder().total(0).completed(0).missingRequired(0).editable(0).pendingReview(0).build())
                .build();
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

    private String ownerArea(DocumentMetadata metadata) {
        return !isBlank(metadata.getOwnerAreaId()) ? metadata.getOwnerAreaId() : metadata.getTenantId();
    }

    private String defaultName(DocumentRequirement requirement) {
        return isBlank(requirement.getName()) ? "Documento requerido" : requirement.getName();
    }

    private String normalizeDirection(String value) {
        return "OUTPUT".equalsIgnoreCase(normalize(value)) ? "OUTPUT" : "INPUT";
    }

    private String normalizeLifecyclePolicy(String value) {
        String normalized = normalize(value);
        if (AVAILABLE_FOR_NEXT_TASKS.equalsIgnoreCase(normalized)) {
            return AVAILABLE_FOR_NEXT_TASKS;
        }
        if (AVAILABLE_FOR_INSTANCE.equalsIgnoreCase(normalized)) {
            return AVAILABLE_FOR_INSTANCE;
        }
        if (PUBLISH_ON_PROCESS_END.equalsIgnoreCase(normalized)) {
            return PUBLISH_ON_PROCESS_END;
        }
        return TASK_ONLY;
    }

    private String defaultLifecyclePolicy(String documentDirection) {
        return "OUTPUT".equals(normalizeDirection(documentDirection))
                ? AVAILABLE_FOR_NEXT_TASKS
                : TASK_ONLY;
    }

    private String extractProcessKey(String processDefinitionId) {
        String value = normalize(processDefinitionId);
        if (value == null) {
            return "";
        }
        int separatorIndex = value.indexOf(':');
        return separatorIndex <= 0 ? value : value.substring(0, separatorIndex);
    }

    private Integer extractProcessVersion(String processDefinitionId) {
        String value = normalize(processDefinitionId);
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.valueOf(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private String coalesce(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null && !normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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

    private enum RuntimePermission {
        VIEW,
        UPLOAD,
        EDIT,
        DOWNLOAD,
        APPROVE,
        REJECT,
        LOCK
    }

    private record TaskContext(
            String processKey,
            Integer processVersion,
            String processInstanceId,
            String taskDefinitionKey,
            String taskInstanceId
    ) {
    }
}
