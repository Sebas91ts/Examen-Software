package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.modules.ai.application.dto.AiBusinessContextPayloadDto;
import com.systembpm.system.modules.ai.application.dto.AiBusinessContextRequestDto;
import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.document.application.dto.DocumentMetadataResponseDto;
import com.systembpm.system.modules.document.application.dto.DocumentSearchRequestDto;
import com.systembpm.system.modules.document.application.dto.DocumentSearchResponseDto;
import com.systembpm.system.modules.document.application.service.DocumentSearchService;
import com.systembpm.system.modules.document.domain.DocumentRequirement;
import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import com.systembpm.system.modules.document.infrastructure.repository.TaskDocumentConfigRepository;
import com.systembpm.system.modules.form.domain.FormDefinition;
import com.systembpm.system.modules.form.infrastructure.repository.FormDefinitionRepository;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBusinessContextServiceImpl implements AiBusinessContextService {

    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final int PROCESS_LIMIT = 30;
    private static final int TASK_LIMIT = 30;
    private static final int DOCUMENT_LIMIT = 25;
    private static final int FORM_LIMIT = 20;
    private static final int AREA_LIMIT = 40;

    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final AreaRepository areaRepository;
    private final CamundaService camundaService;
    private final DocumentSearchService documentSearchService;
    private final FormDefinitionRepository formDefinitionRepository;
    private final TaskDocumentConfigRepository taskDocumentConfigRepository;

    @Override
    public AiBusinessContextPayloadDto buildContext(AiBusinessContextRequestDto request, String requesterEmail) {
        Usuario requester = resolveRequester(requesterEmail);
        AiBusinessContextRequestDto safeRequest = request == null ? new AiBusinessContextRequestDto() : request;

        List<Map<String, Object>> processes = buildProcesses(requester);
        List<Map<String, Object>> tasks = buildTasks(requester);
        List<Map<String, Object>> documents = buildDocuments(safeRequest, requesterEmail);
        List<Map<String, Object>> forms = buildForms(safeRequest);
        List<Map<String, Object>> areas = buildAreas(requester);

        log.info("ai.context.built user={} roles={} areaId={} processes={} tasks={} documents={} forms={}",
                requester.getEmail(), requester.getRoles(), effectiveAreaId(requester),
                processes.size(), tasks.size(), documents.size(), forms.size());

        return AiBusinessContextPayloadDto.builder()
                .message(safeText(safeRequest.getMessage()))
                .user(buildUserContext(requester))
                .currentContext(buildCurrentContext(safeRequest))
                .processes(processes)
                .tasks(tasks)
                .documents(documents)
                .forms(forms)
                .areas(areas)
                .limits(Map.of(
                        "processes", PROCESS_LIMIT,
                        "tasks", TASK_LIMIT,
                        "documents", DOCUMENT_LIMIT,
                        "forms", FORM_LIMIT,
                        "areas", AREA_LIMIT))
                .build();
    }

    private Usuario resolveRequester(String requesterEmail) {
        if (isBlank(requesterEmail)) {
            throw new IllegalArgumentException("Usuario autenticado requerido para contexto IA");
        }
        return usuarioRepository.findByEmail(requesterEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("Usuario autenticado no encontrado"));
    }

    private Map<String, Object> buildUserContext(Usuario requester) {
        return Map.of(
                "email", safeText(requester.getEmail()),
                "name", safeText(joinName(requester)),
                "areaId", safeText(effectiveAreaId(requester)),
                "areaName", safeText(requester.getAreaNombre()),
                "tenantAsAreaId", safeText(requester.getTenantId()),
                "roles", requester.getRoles() == null ? List.of() : requester.getRoles(),
                "admin", isAdmin(requester),
                "client", isClient(requester));
    }

    private Map<String, Object> buildCurrentContext(AiBusinessContextRequestDto request) {
        return Map.of(
                "taskId", safeText(request.getTaskId()),
                "processInstanceId", safeText(request.getProcessInstanceId()),
                "processKey", safeText(request.getProcessKey()),
                "documentId", safeText(request.getDocumentId()),
                "formId", safeText(request.getFormId()),
                "currentFormValues", request.getCurrentFormValues() == null ? Map.of() : request.getCurrentFormValues());
    }

    private List<Map<String, Object>> buildProcesses(Usuario requester) {
        return procesoRepository.findByEstadoIgnoreCase(ESTADO_PUBLICADO)
                .stream()
                .filter(Objects::nonNull)
                .filter(process -> !isClient(requester) || process.isClientStartEnabled())
                .limit(PROCESS_LIMIT)
                .map(process -> Map.<String, Object>of(
                        "id", safeText(process.getId()),
                        "processKey", safeText(process.getProcessKey()),
                        "name", safeText(process.getNombre()),
                        "description", safeText(process.getDescripcion()),
                        "version", process.getVersion() == null ? 0 : process.getVersion(),
                        "clientStartEnabled", process.isClientStartEnabled()))
                .toList();
    }

    private List<Map<String, Object>> buildTasks(Usuario requester) {
        try {
            return camundaService.listarTareasTodas()
                    .stream()
                    .filter(task -> canSeeTask(task, requester))
                    .limit(TASK_LIMIT)
                    .map(this::mapTask)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("ai.context.tasks.unavailable user={}", requester.getEmail(), ex);
            return List.of();
        }
    }

    private boolean canSeeTask(Map<String, Object> task, Usuario requester) {
        if (isAdmin(requester)) {
            return true;
        }
        String assignee = text(task.get("assignee"));
        if (!isBlank(assignee) && assignee.equalsIgnoreCase(requester.getEmail())) {
            return true;
        }
        if (isClient(requester)) {
            return false;
        }
        String areaId = text(task.get("areaId"));
        String requesterAreaId = effectiveAreaId(requester);
        return !isBlank(areaId) && !isBlank(requesterAreaId) && areaId.equalsIgnoreCase(requesterAreaId);
    }

    private Map<String, Object> mapTask(Map<String, Object> task) {
        return Map.of(
                "id", safeText(text(task.get("id"))),
                "name", safeText(text(task.get("name"))),
                "assignee", safeText(text(task.get("assignee"))),
                "processDefinitionId", safeText(text(task.get("processDefinitionId"))),
                "processInstanceId", safeText(text(task.get("processInstanceId"))),
                "taskDefinitionKey", safeText(text(task.get("taskDefinitionKey"))),
                "processName", safeText(text(task.get("nombreProceso"))),
                "areaId", safeText(text(task.get("areaId"))),
                "areaName", safeText(text(task.get("areaNombre"))));
    }

    private List<Map<String, Object>> buildDocuments(AiBusinessContextRequestDto request, String requesterEmail) {
        DocumentSearchRequestDto filters = DocumentSearchRequestDto.builder()
                .processInstanceId(blankToNull(request.getProcessInstanceId()))
                .processKey(blankToNull(request.getProcessKey()))
                .page(0)
                .size(DOCUMENT_LIMIT)
                .sortBy("updatedAt")
                .sortDirection("DESC")
                .build();
        DocumentSearchResponseDto response = documentSearchService.search(filters, requesterEmail);
        if (response.getContent() == null) {
            return List.of();
        }
        return response.getContent().stream()
                .map(this::mapDocument)
                .toList();
    }

    private Map<String, Object> mapDocument(DocumentMetadataResponseDto document) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", safeText(document.getId()));
        item.put("name", safeText(document.getOriginalName()));
        item.put("mimeType", safeText(document.getMimeType()));
        item.put("state", document.getDocumentState() == null ? "" : document.getDocumentState().name());
        item.put("processInstanceId", safeText(document.getProcessInstanceId()));
        item.put("processKey", safeText(document.getProcessKey()));
        item.put("taskDefinitionKey", safeText(document.getTaskDefinitionKey()));
        item.put("requirementId", safeText(document.getDocumentRequirementId()));
        item.put("requirementName", safeText(document.getDocumentRequirementName()));
        item.put("direction", safeText(document.getDocumentDirection()));
        item.put("editable", Boolean.TRUE.equals(document.getEditable()));
        item.put("locked", Boolean.TRUE.equals(document.getLocked()));
        item.put("uploadedBy", safeText(document.getUploadedBy()));
        item.put("updatedBy", safeText(document.getUpdatedBy()));
        return item;
    }

    private List<Map<String, Object>> buildForms(AiBusinessContextRequestDto request) {
        if (!isBlank(request.getProcessKey())) {
            return formDefinitionRepository.findByProcessKeyIgnoreCaseOrderByProcessVersionAsc(request.getProcessKey().trim())
                    .stream()
                    .limit(FORM_LIMIT)
                    .map(this::mapForm)
                    .toList();
        }
        return formDefinitionRepository.findAll().stream()
                .limit(FORM_LIMIT)
                .map(this::mapForm)
                .toList();
    }

    private Map<String, Object> mapForm(FormDefinition form) {
        List<Map<String, Object>> fields = form.getFields() == null ? List.of() : form.getFields().stream()
                .map(field -> Map.<String, Object>of(
                        "name", safeText(field.getName()),
                        "label", safeText(field.getLabel()),
                        "type", safeText(field.getType()),
                        "required", Boolean.TRUE.equals(field.getRequired()),
                        "helpText", safeText(field.getHelpText())))
                .toList();
        List<Map<String, Object>> documentRequirements = taskDocumentConfigRepository
                .findByProcessKeyIgnoreCaseAndProcessVersion(form.getProcessKey(), form.getProcessVersion())
                .stream()
                .filter(config -> form.getTaskDefinitionKey() != null
                        && form.getTaskDefinitionKey().equalsIgnoreCase(config.getTaskDefinitionKey()))
                .flatMap(config -> normalizedRequirements(config).stream())
                .map(this::mapDocumentRequirement)
                .toList();

        return Map.of(
                "id", safeText(form.getId()),
                "processKey", safeText(form.getProcessKey()),
                "processVersion", form.getProcessVersion() == null ? 0 : form.getProcessVersion(),
                "taskDefinitionKey", safeText(form.getTaskDefinitionKey()),
                "title", safeText(form.getTitle()),
                "fields", fields,
                "documentRequirements", documentRequirements);
    }

    private List<DocumentRequirement> normalizedRequirements(TaskDocumentConfig config) {
        if (config.getDocumentRequirements() != null && !config.getDocumentRequirements().isEmpty()) {
            return config.getDocumentRequirements();
        }
        if (isBlank(config.getDocumentName())) {
            return List.of();
        }
        return List.of(DocumentRequirement.builder()
                .id(config.getId())
                .name(config.getDocumentName())
                .description(config.getDescription())
                .documentDirection(config.getDocumentDirection())
                .required(config.getRequired())
                .editable(config.getEditable())
                .requireApproval(config.getRequireApproval())
                .allowedMimeTypes(config.getAllowedMimeTypes())
                .build());
    }

    private Map<String, Object> mapDocumentRequirement(DocumentRequirement requirement) {
        return Map.of(
                "id", safeText(requirement.getId()),
                "name", safeText(requirement.getName()),
                "description", safeText(requirement.getDescription()),
                "direction", safeText(requirement.getDocumentDirection()),
                "required", Boolean.TRUE.equals(requirement.getRequired()),
                "editable", Boolean.TRUE.equals(requirement.getEditable()),
                "requireApproval", Boolean.TRUE.equals(requirement.getRequireApproval()),
                "allowedMimeTypes", requirement.getAllowedMimeTypes() == null ? List.of() : requirement.getAllowedMimeTypes());
    }

    private List<Map<String, Object>> buildAreas(Usuario requester) {
        if (!isAdmin(requester)) {
            String areaId = effectiveAreaId(requester);
            return areaRepository.findById(areaId == null ? "" : areaId)
                    .map(area -> List.<Map<String, Object>>of(mapArea(area)))
                    .orElseGet(List::of);
        }
        return areaRepository.findByActivaTrue().stream()
                .limit(AREA_LIMIT)
                .map(this::mapArea)
                .toList();
    }

    private Map<String, Object> mapArea(Area area) {
        return Map.of(
                "id", safeText(area.getId()),
                "name", safeText(area.getNombre()),
                "description", safeText(area.getDescripcion()));
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
        return blankToNull(usuario.getTenantId());
    }

    private String joinName(Usuario usuario) {
        return (safeText(usuario.getNombre()) + " " + safeText(usuario.getApellido())).trim();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
