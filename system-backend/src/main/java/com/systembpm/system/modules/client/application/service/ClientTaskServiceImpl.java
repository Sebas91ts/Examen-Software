package com.systembpm.system.modules.client.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.client.application.dto.ClientTaskCompleteResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskFormResponseDto;
import com.systembpm.system.modules.client.application.dto.ClientTaskListItemDto;
import com.systembpm.system.modules.client.domain.ClientProcessInstance;
import com.systembpm.system.modules.client.infrastructure.repository.ClientProcessInstanceRepository;
import com.systembpm.system.modules.file.application.dto.FileUploadResponseDto;
import com.systembpm.system.modules.file.application.service.IFileUploadService;
import com.systembpm.system.modules.form.application.dto.FormDefinitionResponseDto;
import com.systembpm.system.modules.form.application.service.IFormDefinitionService;
import com.systembpm.system.modules.notification.application.service.NotificationServiceImpl;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.taskexecutionlog.application.service.ITaskExecutionLogService;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.MultiValueMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.xml.sax.InputSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTaskServiceImpl implements ClientTaskService {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private final ClientProcessInstanceRepository clientProcessInstanceRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final CamundaService camundaService;
    private final IFormDefinitionService formDefinitionService;
    private final IFileUploadService fileUploadService;
    private final ITaskExecutionLogService taskExecutionLogService;
    private final NotificationServiceImpl notificationService;
    private final IRealtimeEventService realtimeEventService;

    @Override
    public List<ClientTaskListItemDto> listarTareasCliente(String clientEmail) {
        Usuario cliente = obtenerCliente(clientEmail);
        Set<String> processInstanceIds = clientProcessInstanceRepository.findByClientUserIdOrderByStartedAtDesc(cliente.getId()).stream()
                .map(ClientProcessInstance::getProcessInstanceId)
                .filter(this::hasText)
                .collect(Collectors.toSet());

        if (processInstanceIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> activeTasks = camundaService.listarTareasTodas();
        if (activeTasks == null || activeTasks.isEmpty()) {
            return List.of();
        }

        return activeTasks.stream()
                .filter(task -> processInstanceIds.contains(stringValue(task.get("processInstanceId"))))
                .filter(task -> isTaskVisibleToClient(task, cliente.getEmail()))
                .map(task -> mapToListItem(task, cliente.getEmail()))
                .toList();
    }

    @Override
    public ClientTaskFormResponseDto obtenerFormularioTarea(String taskId, String clientEmail) {
        Usuario cliente = obtenerCliente(clientEmail);
        Map<String, Object> task = obtenerTareaCliente(taskId, cliente.getEmail());
        FormDefinitionResponseDto formDefinition = resolveFormDefinition(task);

        Map<String, Object> currentValues = Map.of();
        try {
            currentValues = camundaService.obtenerVariablesTarea(taskId);
        } catch (Throwable ex) {
            log.warn("No se pudieron leer variables actuales de la tarea {} para cliente {}", taskId, clientEmail, ex);
        }

        return ClientTaskFormResponseDto.builder()
                .taskId(stringValue(task.get("id")))
                .taskName(resolveTaskName(task))
                .processInstanceId(stringValue(task.get("processInstanceId")))
                .processKey(resolveProcessKey(task))
                .processVersion(resolveProcessVersion(task))
                .processName(stringValue(task.get("nombreProceso")))
                .areaName(stringValue(task.get("areaNombre")))
                .assignee(stringValue(task.get("assignee")))
                .formDefinition(formDefinition)
                .currentValues(sanitizeValues(currentValues))
                .build();
    }

    @Override
    public ClientTaskCompleteResponseDto completarTarea(
            String taskId,
            String clientEmail,
            Map<String, Object> formData,
            MultiValueMap<String, MultipartFile> files) {
        Usuario cliente = obtenerCliente(clientEmail);
        Map<String, Object> task = obtenerTareaCliente(taskId, cliente.getEmail());

        Map<String, Object> variables = new LinkedHashMap<>();
        if (formData != null) {
            variables.putAll(sanitizeValues(formData));
        }

        if (files != null && !files.isEmpty()) {
            for (Map.Entry<String, MultipartFile> entry : files.toSingleValueMap().entrySet()) {
                if (!hasText(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }

                variables.put(entry.getKey().trim(), toFileMetadataMap(fileUploadService.upload(entry.getValue())));
            }
        }

        log.info("Completando tarea cliente {} para instancia {}", taskId, stringValue(task.get("processInstanceId")));
        camundaService.completarTarea(taskId, variables, cliente.getEmail());
        taskExecutionLogService.registrarEjecucion(task, variables, cliente.getEmail());
        notificationService.notifyTaskCompleted(task, cliente.getEmail());
        realtimeEventService.publishTaskCompleted(task, cliente.getEmail());

        return ClientTaskCompleteResponseDto.builder()
                .taskId(taskId)
                .processInstanceId(stringValue(task.get("processInstanceId")))
                .processName(stringValue(task.get("nombreProceso")))
                .completedBy(cliente.getEmail())
                .completedAt(LocalDateTime.now())
                .message("Tarea completada exitosamente")
                .build();
    }

    private ClientTaskListItemDto mapToListItem(Map<String, Object> task, String clientEmail) {
        FormDefinitionResponseDto formDefinition = resolveFormDefinition(task);
        return ClientTaskListItemDto.builder()
                .taskId(stringValue(task.get("id")))
                .taskName(resolveTaskName(task))
                .processInstanceId(stringValue(task.get("processInstanceId")))
                .processKey(resolveProcessKey(task))
                .processVersion(resolveProcessVersion(task))
                .processName(stringValue(task.get("nombreProceso")))
                .areaName(stringValue(task.get("areaNombre")))
                .assignee(stringValue(task.get("assignee")))
                .assignedToClient(isAssignedToClient(task, clientEmail))
                .formDefinition(formDefinition)
                .build();
    }

    private FormDefinitionResponseDto resolveFormDefinition(Map<String, Object> task) {
        String processKey = resolveProcessKey(task);
        Integer processVersion = resolveProcessVersion(task);
        String taskDefinitionKey = stringValue(task.get("taskDefinitionKey"));

        if (!hasText(processKey) || processVersion == null || !hasText(taskDefinitionKey)) {
            return null;
        }

        return formDefinitionService.obtenerPorClave(processKey, processVersion, taskDefinitionKey)
                .orElse(null);
    }

    private Map<String, Object> obtenerTareaCliente(String taskId, String clientEmail) {
        Map<String, Object> task = camundaService.obtenerTarea(taskId);
        if (task == null || task.isEmpty()) {
            throw new IllegalArgumentException("La tarea no existe");
        }

        String processInstanceId = stringValue(task.get("processInstanceId"));
        if (!hasText(processInstanceId)) {
            throw new IllegalArgumentException("La tarea no tiene instancia asociada");
        }

        ClientProcessInstance clientInstance = clientProcessInstanceRepository
                .findByProcessInstanceIdAndClientEmail(processInstanceId.trim(), clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a esta tarea"));

        if (!isTaskVisibleToClient(task, clientEmail) && !isAssignedToClient(task, clientEmail)) {
            throw new IllegalArgumentException("No tienes acceso a esta tarea");
        }

        // Refuerza que la tarea pertenezca al proceso del cliente.
        if (!hasText(clientInstance.getProcessInstanceId())) {
            throw new IllegalArgumentException("No tienes acceso a esta tarea");
        }

        return task;
    }

    private boolean isTaskVisibleToClient(Map<String, Object> task, String clientEmail) {
        if (task == null || task.isEmpty()) {
            return false;
        }

        if (isAssignedToClient(task, clientEmail)) {
            return true;
        }

        String processDefinitionId = stringValue(task.get("processDefinitionId"));
        String taskDefinitionKey = stringValue(task.get("taskDefinitionKey"));
        if (!hasText(taskDefinitionKey)) {
            return false;
        }

        String processKey = resolveProcessKey(task);
        Integer processVersion = resolveProcessVersion(task);
        Proceso proceso = resolveProceso(processKey, processVersion);
        if (proceso == null || !hasText(proceso.getXml())) {
            return false;
        }

        String laneName = resolveLaneName(proceso.getXml(), taskDefinitionKey.trim());
        return isClientLaneName(laneName);
    }

    private boolean isAssignedToClient(Map<String, Object> task, String clientEmail) {
        String assignee = stringValue(task.get("assignee"));
        return hasText(assignee) && hasText(clientEmail) && assignee.trim().equalsIgnoreCase(clientEmail.trim());
    }

    private Proceso resolveProceso(String processKey, Integer version) {
        if (!hasText(processKey)) {
            return null;
        }

        List<Proceso> procesos = procesoRepository.findByProcessKey(processKey.trim());
        if (procesos == null || procesos.isEmpty()) {
            return null;
        }

        if (version != null) {
            for (Proceso proceso : procesos) {
                if (Objects.equals(proceso.getVersion(), version)) {
                    return proceso;
                }
            }
        }

        return procesos.stream()
                .max(java.util.Comparator.comparing(proceso -> proceso.getVersion() != null ? proceso.getVersion() : 0))
                .orElse(null);
    }

    private String resolveLaneName(String xml, String taskDefinitionKey) {
        try {
            Document document = parseDocument(xml);
            NodeList lanes = document.getElementsByTagNameNS(BPMN_NAMESPACE, "lane");
            for (int i = 0; i < lanes.getLength(); i++) {
                Element lane = (Element) lanes.item(i);
                if (laneContieneNodo(lane, taskDefinitionKey)) {
                    return lane.getAttribute("name");
                }
            }
        } catch (Exception ex) {
            log.warn("No se pudo resolver la lane para la tarea {} del proceso cliente", taskDefinitionKey, ex);
        }

        return null;
    }

    private boolean isClientLaneName(String laneName) {
        if (!hasText(laneName)) {
            return false;
        }

        String normalized = normalizeText(laneName);
        return normalized.equals("cliente")
                || normalized.startsWith("cliente ")
                || normalized.startsWith("cliente-")
                || normalized.equals("clienteexterno")
                || normalized.startsWith("cliente externo")
                || normalized.startsWith("clientes");
    }

    private String normalizeText(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("[\\u0300-\\u036f]", "")
                .toLowerCase();
    }

    private Map<String, Object> sanitizeValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }

            sanitized.put(entry.getKey().trim(), sanitizeValue(entry.getValue()));
        }

        return sanitized;
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nested : mapValue.entrySet()) {
                if (nested.getKey() == null) {
                    continue;
                }
                sanitized.put(String.valueOf(nested.getKey()), sanitizeValue(nested.getValue()));
            }
            return sanitized;
        }

        if (value instanceof List<?> listValue) {
            List<Object> sanitizedList = new ArrayList<>();
            for (Object item : listValue) {
                Object sanitizedItem = sanitizeValue(item);
                if (sanitizedItem != null) {
                    sanitizedList.add(sanitizedItem);
                }
            }
            return sanitizedList;
        }

        return String.valueOf(value);
    }

    private Usuario obtenerCliente(String clientEmail) {
        if (!hasText(clientEmail)) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }

        return usuarioRepository.findByEmail(clientEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException("No se encontro el usuario cliente autenticado"));
    }

    private boolean laneContieneNodo(Element laneElement, String nodeId) {
        NodeList flowNodeRefs = laneElement.getElementsByTagNameNS(BPMN_NAMESPACE, "flowNodeRef");
        for (int i = 0; i < flowNodeRefs.getLength(); i++) {
            String ref = flowNodeRefs.item(i).getTextContent();
            if (nodeId.equals(ref != null ? ref.trim() : null)) {
                return true;
            }
        }
        return false;
    }

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String resolveTaskName(Map<String, Object> task) {
        String name = stringValue(task.get("name"));
        if (hasText(name)) {
            return name.trim();
        }

        name = stringValue(task.get("nombreTarea"));
        if (hasText(name)) {
            return name.trim();
        }

        name = stringValue(task.get("taskDefinitionKey"));
        return hasText(name) ? name.trim() : "Tarea sin nombre";
    }

    private String resolveProcessKey(Map<String, Object> task) {
        String processKey = stringValue(task.get("processKey"));
        if (hasText(processKey)) {
            return processKey.trim();
        }

        return extractProcessKey(stringValue(task.get("processDefinitionId")));
    }

    private Integer resolveProcessVersion(Map<String, Object> task) {
        Object rawVersion = task.get("processVersion");
        if (rawVersion instanceof Number number) {
            return number.intValue();
        }
        if (rawVersion instanceof String versionText && hasText(versionText)) {
            try {
                return Integer.valueOf(versionText.trim());
            } catch (NumberFormatException ignored) {
                // Fallback a processDefinitionId abajo.
            }
        }

        return extractProcessVersion(stringValue(task.get("processDefinitionId")));
    }

    private String extractProcessKey(String processDefinitionId) {
        if (!hasText(processDefinitionId)) {
            return "";
        }

        int separatorIndex = processDefinitionId.indexOf(':');
        if (separatorIndex <= 0) {
            return processDefinitionId.trim();
        }

        return processDefinitionId.substring(0, separatorIndex).trim();
    }

    private Integer extractProcessVersion(String processDefinitionId) {
        if (!hasText(processDefinitionId)) {
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

    private Map<String, Object> toFileMetadataMap(FileUploadResponseDto uploadResponse) {
        if (uploadResponse == null) {
            return Map.of();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("publicId", uploadResponse.getPublicId());
        metadata.put("fileName", uploadResponse.getFileName());
        metadata.put("secureUrl", uploadResponse.getSecureUrl());
        metadata.put("mimeType", uploadResponse.getMimeType());
        metadata.put("size", uploadResponse.getSize());
        metadata.put("resourceType", uploadResponse.getResourceType());
        return metadata;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
