package com.systembpm.system.modules.camunda.application.service;

import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.bpmn.application.service.BpmnXmlSanitizerService;
import com.systembpm.system.modules.document.application.service.DocumentLifecycleService;
import com.systembpm.system.modules.document.application.service.DocumentTaskRuntimeService;
import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import com.systembpm.system.modules.security.application.service.AuthService;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;

@Slf4j
@Service
@RequiredArgsConstructor
public class CamundaServiceImpl implements CamundaService {
    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    private final RestTemplate restTemplate;
    private final ProcesoRepository procesoRepository;
    private final AreaRepository areaRepository;
    private final AuthService authService;
    private final BpmnXmlSanitizerService bpmnXmlSanitizerService;
    private final DocumentLifecycleService documentLifecycleService;
    private final DocumentTaskRuntimeService documentTaskRuntimeService;

    @Value("${CAMUNDA_BASE_URL:${camunda.base-url:http://localhost:8081/engine-rest}}")
    private String camundaBaseUrl;

    @Value("${camunda.history-time-to-live-days:180}")
    private Integer historyTimeToLiveDays;

    @Override
    public Map<String, Object> desplegarProceso(String procesoId) {
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new IllegalArgumentException("Proceso no encontrado con ID: " + procesoId));

        String xmlSanitizado = prepareDeploymentXml(proceso);
        if (xmlSanitizado == null || xmlSanitizado.isBlank()) {
            throw new IllegalArgumentException("El proceso no contiene XML BPMN valido");
        }

        validarExclusiveGateways(xmlSanitizado);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("bpmn-" + proceso.getId(), ".bpmn");
            Files.writeString(tempFile, xmlSanitizado, StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("deployment-name", proceso.getNombre() + "-v" + proceso.getVersion());
            body.add("deployment-source", "system-bpm");
            body.add("data", new FileSystemResource(tempFile.toFile()));

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/deployment/create",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {
                    });

            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo el despliegue: " + ex.getResponseBodyAsString(), ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo preparar el archivo BPMN para despliegue", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    log.warn("No se pudo eliminar el archivo temporal de despliegue: {}", tempFile);
                }
            }
        }
    }

    @Override
    public Map<String, Object> iniciarInstancia(String processKey) {
        return iniciarInstanciaPorDefinicion(processKey, null);
    }

    @Override
    public Map<String, Object> iniciarInstanciaPorDefinicion(String processKey, String businessKey) {
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("El processKey es obligatorio");
        }

        asegurarProcesoPublicadoDesplegado(processKey.trim());
        Map<String, Object> body = businessKey == null || businessKey.isBlank()
                ? Map.of()
                : Map.of("businessKey", businessKey);
        return iniciarInstanciaConRecuperacion(processKey.trim(), body);
    }

    @Override
    public Map<String, Object> iniciarInstanciaConVariables(String processKey, Map<String, Object> variables) {
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("El processKey es obligatorio");
        }

        asegurarProcesoPublicadoDesplegado(processKey.trim());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        Map<String, Object> normalizedVariables = normalizeVariables(variables);
        if (!normalizedVariables.isEmpty()) {
            body.put("variables", normalizedVariables);
        }
        return iniciarInstanciaConRecuperacion(processKey.trim(), body);
    }

    @Override
    public List<Map<String, Object>> listarInstanciasProcesoActivas() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    camundaBaseUrl + "/process-instance",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> instancias = response.getBody() != null ? response.getBody() : List.of();
            return instancias.stream()
                    .map(this::enriquecerInstanciaProceso)
                    .toList();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la consulta de instancias activas: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public Map<String, Object> obtenerInstanciaProceso(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("El processInstanceId es obligatorio");
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/process-instance/" + processInstanceId,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });

            return enriquecerInstanciaProceso(response.getBody() != null ? response.getBody() : Map.of());
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la consulta de la instancia: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public List<Map<String, Object>> listarTareas() {
        return listarTareasTodas();
    }

    @Override
    public List<Map<String, Object>> listarTareasPorAssignee(String assignee) {
        if (assignee == null || assignee.isBlank()) {
            throw new IllegalArgumentException("El assignee es obligatorio");
        }

        return listarTareasTodas().stream()
                .filter(tarea -> assignee.equalsIgnoreCase(stringValue(tarea.get("assignee"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listarTareasPorArea(String areaId) {
        if (areaId == null || areaId.isBlank()) {
            throw new IllegalArgumentException("El areaId es obligatorio");
        }

        return listarTareasTodas().stream()
                .filter(tarea -> areaId.equalsIgnoreCase(stringValue(tarea.get("areaId"))))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listarTareasTodas() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });
            List<Map<String, Object>> tareas = response.getBody() != null ? response.getBody() : List.of();
            return tareas.stream()
                    .map(this::enriquecerTareaConArea)
                    .toList();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la consulta de tareas: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public Map<String, Object> obtenerTarea(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId,
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });
            return enriquecerTareaConArea(response.getBody() != null ? response.getBody() : Map.of());
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la consulta del detalle de la tarea: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public Map<String, Object> obtenerVariablesTarea(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }

        try {
            ResponseEntity<Map<String, Map<String, Object>>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId + "/variables",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });

            Map<String, Map<String, Object>> rawVariables = response.getBody();
            if (rawVariables == null || rawVariables.isEmpty()) {
                return Map.of();
            }

            Map<String, Object> variables = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : rawVariables.entrySet()) {
                Map<String, Object> variable = entry.getValue();
                if (variable == null || !variable.containsKey("value")) {
                    continue;
                }
                variables.put(entry.getKey(), variable.get("value"));
            }

            return variables;
        } catch (HttpStatusCodeException ex) {
            log.warn("No se pudieron obtener las variables de la tarea {}: {}", taskId, ex.getResponseBodyAsString());
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> completarTarea(String taskId) {
        return completarTarea(taskId, Map.of(), null);
    }

    @Override
    public Map<String, Object> completarTarea(String taskId, Map<String, Object> variables) {
        return completarTarea(taskId, variables, null);
    }

    @Override
    public Map<String, Object> completarTarea(String taskId, Map<String, Object> variables, String completedBy) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }

        try {
            Map<String, Object> taskSnapshot = obtenerTarea(taskId);
            if (completedBy != null && !completedBy.isBlank()) {
                documentTaskRuntimeService.validateBeforeComplete(taskSnapshot, completedBy);
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            Map<String, Object> normalizedVariables = normalizeVariables(variables);
            log.info("Enviando variables a Camunda para tarea {}: {}", taskId, normalizedVariables);
            if (!normalizedVariables.isEmpty()) {
                payload.put("variables", normalizedVariables);
            }

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId + "/complete",
                    HttpMethod.POST,
                    new HttpEntity<>(payload),
                    new ParameterizedTypeReference<>() {
                    });
            try {
                documentLifecycleService.onTaskCompleted(taskSnapshot, completedBy);
            } catch (RuntimeException ex) {
                log.warn("No se pudo actualizar lifecycle documental para tarea {}. La tarea ya fue completada en Camunda.", taskId, ex);
            }
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la finalizacion de la tarea: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public Map<String, Object> tomarTarea(String taskId, String userEmail) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("El usuario autenticado es obligatorio");
        }

        Map<String, Object> tarea = obtenerTarea(taskId);
        String assignee = stringValue(tarea.get("assignee"));
        if (assignee != null && !assignee.isBlank()) {
            throw new IllegalArgumentException("La tarea ya fue tomada por otro usuario");
        }

        UsuarioResponseDto usuario = authService.obtenerUsuarioAutenticado(userEmail);
        String tareaAreaId = stringValue(tarea.get("areaId"));
        if (tareaAreaId == null || tareaAreaId.isBlank() || tarea.get("areaNombre") == null) {
            throw new IllegalArgumentException("La tarea no tiene un area identificable");
        }

        if (usuario.getAreaId() == null || usuario.getAreaId().isBlank()) {
            throw new IllegalArgumentException("Tu usuario no tiene un area asignada");
        }

        if (!usuario.getAreaId().equalsIgnoreCase(tareaAreaId.trim())) {
            throw new IllegalArgumentException(
                    "Solo usuarios del area " + tarea.get("areaNombre") + " pueden tomar esta tarea");
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId + "/claim",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("userId", userEmail.trim())),
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la toma de la tarea: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private Map<String, Object> enriquecerTareaConArea(Map<String, Object> tarea) {
        if (tarea == null || tarea.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> enriquecida = new java.util.LinkedHashMap<>(tarea);
        String processDefinitionId = stringValue(tarea.get("processDefinitionId"));
        String taskDefinitionKey = stringValue(tarea.get("taskDefinitionKey"));

        String processKey = extraerProcessKey(processDefinitionId);
        enriquecida.put("nombreProceso", resolverNombreProceso(processKey));

        Area area = resolverAreaDesdeBpmn(processKey, taskDefinitionKey);
        if (area != null) {
            enriquecida.put("areaId", area.getId());
            enriquecida.put("areaNombre", area.getNombre());
        } else {
            enriquecida.put("areaId", null);
            enriquecida.put("areaNombre", "Área no identificada");
        }

        return enriquecida;
    }

    private Map<String, Object> enriquecerInstanciaProceso(Map<String, Object> instancia) {
        if (instancia == null || instancia.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> enriquecida = new java.util.LinkedHashMap<>(instancia);
        String processDefinitionId = stringValue(instancia.get("definitionId"));
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            processDefinitionId = stringValue(instancia.get("processDefinitionId"));
        }

        String processKey = extraerProcessKey(processDefinitionId);
        enriquecida.put("processDefinitionId", processDefinitionId);
        enriquecida.put("processKey", processKey);
        enriquecida.put("processVersion", extraerProcessVersion(processDefinitionId));
        enriquecida.put("nombreProceso", resolverNombreProceso(processKey));
        enriquecida.put("estado", "ACTIVA");
        return enriquecida;
    }

    private String resolverNombreProceso(String processKey) {
        if (processKey == null || processKey.isBlank()) {
            return "Proceso no identificado";
        }

        return procesoRepository.findTopByProcessKeyOrderByVersionDesc(processKey.trim())
                .map(Proceso::getNombre)
                .filter(nombre -> nombre != null && !nombre.isBlank())
                .orElseGet(() -> procesarNombreFallback(processKey));
    }

    private String procesarNombreFallback(String processKey) {
        return processKey.replace('_', ' ').trim().isBlank()
                ? "Proceso no identificado"
                : processKey;
    }

    private Area resolverAreaDesdeBpmn(String processKey, String taskDefinitionKey) {
        if (processKey == null || processKey.isBlank() || taskDefinitionKey == null || taskDefinitionKey.isBlank()) {
            return null;
        }

        Proceso procesoPublicado = procesoRepository.findTopByProcessKeyOrderByVersionDesc(processKey.trim())
                .orElse(null);
        if (procesoPublicado == null || procesoPublicado.getXml() == null || procesoPublicado.getXml().isBlank()) {
            return null;
        }

        try {
            Document document = parseDocument(procesoPublicado.getXml());
            Element taskElement = encontrarElementoPorId(document, taskDefinitionKey.trim());
            if (taskElement == null) {
                return null;
            }

            Element laneElement = encontrarLaneQueContieneNodo(document, taskDefinitionKey.trim());
            if (laneElement == null) {
                return null;
            }

            String areaId = leerAreaIdDeLane(laneElement);
            if (areaId == null || areaId.isBlank()) {
                return null;
            }

            return areaRepository.findById(areaId.trim())
                    .filter(area -> area.getActiva() == null || area.getActiva())
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("No se pudo resolver el area para la tarea {} del proceso {}", taskDefinitionKey, processKey, ex);
            return null;
        }
    }

    private Element encontrarLaneQueContieneNodo(Document document, String nodeId) {
        NodeList lanes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "lane");
        for (int i = 0; i < lanes.getLength(); i++) {
            Element lane = (Element) lanes.item(i);
            NodeList flowNodeRefs = lane.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "flowNodeRef");
            for (int j = 0; j < flowNodeRefs.getLength(); j++) {
                String ref = flowNodeRefs.item(j).getTextContent();
                if (nodeId.equals(ref != null ? ref.trim() : null)) {
                    return lane;
                }
            }
        }
        return null;
    }

    private String leerAreaIdDeLane(Element laneElement) {
        if (laneElement == null) {
            return null;
        }

        NodeList areaRefs = laneElement.getElementsByTagNameNS("http://systembpm.com/schema", "areaRef");
        if (areaRefs.getLength() == 0) {
            return null;
        }

        String value = areaRefs.item(0).getTextContent();
        return value != null ? value.trim() : null;
    }

    private Map<String, Object> iniciarInstanciaConRecuperacion(String processKey, Map<String, Object> body) {
        try {
            return ejecutarInicioInstancia(processKey, body);
        } catch (IllegalArgumentException ex) {
            if (!debeReintentarDespliegue(ex)) {
                throw ex;
            }

            Proceso procesoPublicado = encontrarProcesoPublicadoPorKey(processKey);
            if (procesoPublicado == null) {
                throw ex;
            }

            log.warn("No se encontro la definicion en Camunda para processKey={}. Se intentara redeploy automatico del proceso publicado {} y un nuevo intento de inicio.",
                    processKey, procesoPublicado.getId());

            desplegarProceso(procesoPublicado.getId());
            return ejecutarInicioInstancia(processKey, body);
        }
    }

    private Map<String, Object> ejecutarInicioInstancia(String processKey, Map<String, Object> body) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/process-definition/key/" + processKey + "/start",
                    HttpMethod.POST,
                    new HttpEntity<>(body == null ? Map.of() : body),
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo el inicio de instancia: " + ex.getResponseBodyAsString(), ex);
        }
    }

    private boolean debeReintentarDespliegue(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase();
        return normalized.contains("no matching process definition")
                || normalized.contains("no se encontro la definicion")
                || normalized.contains("no matching process definition with key");
    }

    private Proceso encontrarProcesoPublicadoPorKey(String processKey) {
        return procesoRepository.findByProcessKeyOrderByVersionAsc(processKey).stream()
                .filter(proceso -> proceso.getEstado() != null && ESTADO_PUBLICADO.equalsIgnoreCase(proceso.getEstado()))
                .reduce((actual, siguiente) -> siguiente)
                .orElse(null);
    }

    private void asegurarProcesoPublicadoDesplegado(String processKey) {
        Proceso procesoPublicado = encontrarProcesoPublicadoPorKey(processKey);
        if (procesoPublicado == null) {
            return;
        }

        log.info("Sincronizando definicion publicada antes de iniciar instancia. processKey={} procesoId={} version={}",
                processKey, procesoPublicado.getId(), procesoPublicado.getVersion());
        desplegarProceso(procesoPublicado.getId());
    }

    private String extraerProcessKey(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return "";
        }

        int separatorIndex = processDefinitionId.indexOf(':');
        if (separatorIndex <= 0) {
            return processDefinitionId.trim();
        }

        return processDefinitionId.substring(0, separatorIndex).trim();
    }

    private Integer extraerProcessVersion(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
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

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private String prepareDeploymentXml(Proceso proceso) {
        String xmlSanitizado = bpmnXmlSanitizerService.sanitize(proceso.getXml());
        if (xmlSanitizado == null || xmlSanitizado.isBlank()) {
            return xmlSanitizado;
        }

        String normalizedProcessKey = stringValue(proceso.getProcessKey());
        if (normalizedProcessKey == null || normalizedProcessKey.isBlank()) {
            return xmlSanitizado;
        }

        try {
            Document document = parseDocument(xmlSanitizado);
            ensureCamundaNamespace(document);
            NodeList processNodes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "process");
            for (int i = 0; i < processNodes.getLength(); i++) {
                Node node = processNodes.item(i);
                if (node instanceof Element processElement) {
                    processElement.setAttribute("id", normalizedProcessKey.trim());
                    processElement.setAttribute("isExecutable", "true");
                    ensureHistoryTimeToLive(processElement);
                    if (proceso.getNombre() != null && !proceso.getNombre().isBlank()) {
                        processElement.setAttribute("name", proceso.getNombre().trim());
                    }
                }
            }

            NodeList participants = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "participant");
            for (int i = 0; i < participants.getLength(); i++) {
                Node node = participants.item(i);
                if (node instanceof Element participantElement) {
                    participantElement.setAttribute("processRef", normalizedProcessKey.trim());
                    if (proceso.getNombre() != null && !proceso.getNombre().isBlank()) {
                        participantElement.setAttribute("name", proceso.getNombre().trim());
                    }
                }
            }

            return serializeDocument(document);
        } catch (Exception ex) {
            log.warn("No se pudo alinear el processKey BPMN del proceso {} antes del despliegue. Se usara el XML sanitizado actual.", proceso.getId(), ex);
            return xmlSanitizado;
        }
    }

    private void ensureCamundaNamespace(Document document) {
        if (document == null || document.getDocumentElement() == null) {
            return;
        }
        Element definitions = document.getDocumentElement();
        if (!definitions.hasAttribute("xmlns:camunda")) {
            definitions.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:camunda", CAMUNDA_NS);
        }
    }

    private void ensureHistoryTimeToLive(Element processElement) {
        if (processElement == null) {
            return;
        }
        String existing = processElement.getAttributeNS(CAMUNDA_NS, "historyTimeToLive");
        if (existing != null && !existing.isBlank()) {
            return;
        }
        int ttlDays = historyTimeToLiveDays != null && historyTimeToLiveDays > 0
                ? historyTimeToLiveDays
                : 180;
        processElement.setAttributeNS(CAMUNDA_NS, "camunda:historyTimeToLive", String.valueOf(ttlDays));
    }

    private String serializeDocument(Document document) throws Exception {
        javax.xml.transform.TransformerFactory factory = javax.xml.transform.TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        javax.xml.transform.Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");

        java.io.StringWriter writer = new java.io.StringWriter();
        transformer.transform(new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(writer));
        return writer.toString();
    }

    private void validarExclusiveGateways(String xml) {
        try {
            Document document = parseDocument(xml);
            NodeList gateways = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "exclusiveGateway");
            for (int i = 0; i < gateways.getLength(); i++) {
                Element gateway = (Element) gateways.item(i);
                validarExclusiveGateway(document, gateway);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo validar el BPMN antes de desplegar", ex);
        }
    }

    private void validarExclusiveGateway(Document document, Element gateway) {
        String gatewayId = gateway.getAttribute("id");
        NodeList allSequenceFlows = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "sequenceFlow");

        List<Element> outgoingFlows = new ArrayList<>();
        for (int i = 0; i < allSequenceFlows.getLength(); i++) {
            Element sequenceFlow = (Element) allSequenceFlows.item(i);
            if (gatewayId.equals(sequenceFlow.getAttribute("sourceRef"))) {
                outgoingFlows.add(sequenceFlow);
            }
        }

        if (outgoingFlows.size() <= 1) {
            return;
        }

        String defaultFlowId = gateway.getAttribute("default");
        Element defaultFlow = defaultFlowId == null || defaultFlowId.isBlank()
                ? null
                : outgoingFlows.stream()
                        .filter(flow -> defaultFlowId.equals(flow.getAttribute("id")))
                        .findFirst()
                        .orElse(null);

        List<String> invalidFlows = new ArrayList<>();
        for (Element sequenceFlow : outgoingFlows) {
            if (defaultFlow != null && defaultFlowId.equals(sequenceFlow.getAttribute("id"))) {
                continue;
            }

            if (!hasConditionExpression(sequenceFlow)) {
                invalidFlows.add(sequenceFlow.getAttribute("id"));
            }
        }

        if (!invalidFlows.isEmpty()) {
            String gatewayLabel = gatewayId == null || gatewayId.isBlank() ? "ExclusiveGateway sin id" : gatewayId;
            if (defaultFlowId == null || defaultFlowId.isBlank()) {
                throw new IllegalArgumentException(
                        "Exclusive Gateway '" + gatewayLabel
                                + "' tiene salidas sin conditionExpression ni flujo default: "
                                + String.join(", ", invalidFlows));
            }

            throw new IllegalArgumentException(
                    "Exclusive Gateway '" + gatewayLabel
                            + "' tiene salidas sin conditionExpression fuera del flujo default '"
                            + defaultFlowId + "': "
                            + String.join(", ", invalidFlows));
        }

        if (defaultFlowId != null && !defaultFlowId.isBlank() && defaultFlow == null) {
            throw new IllegalArgumentException(
                    "Exclusive Gateway '" + (gatewayId == null || gatewayId.isBlank() ? "sin id" : gatewayId)
                            + "' referencia como default al flujo '" + defaultFlowId
                            + "', pero ese flujo no existe o no sale de ese gateway");
        }
    }

    private boolean hasConditionExpression(Element sequenceFlow) {
        NodeList conditions = sequenceFlow.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "conditionExpression");
        return conditions != null && conditions.getLength() > 0;
    }

    private Element encontrarElementoPorId(Document document, String elementId) {
        NodeList allNodes = document.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element element = (Element) allNodes.item(i);
            if (elementId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> normalizeVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            if (value instanceof Map<?, ?> mapValue) {
                normalized.put(key, buildSerializedFileVariable(mapValue));
                continue;
            }

            Map<String, Object> camundaVariable = new java.util.LinkedHashMap<>();
            camundaVariable.put("value", normalizeVariableValue(value));
            camundaVariable.put("type", resolveVariableType(value));
            normalized.put(key, camundaVariable);
        }
        return normalized;
    }

    private Map<String, Object> buildSerializedFileVariable(Map<?, ?> value) {
        Map<String, Object> camundaVariable = new java.util.LinkedHashMap<>();
        camundaVariable.put("value", serializeToJson(value));
        camundaVariable.put("type", "String");
        return camundaVariable;
    }

    private String serializeToJson(Map<?, ?> value) {
        Map<String, Object> normalizedValue = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalizedValue.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : normalizedValue.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('\"')
                    .append(escapeJson(entry.getKey()))
                    .append('\"')
                    .append(':')
                    .append(toJsonValue(entry.getValue()));
        }
        json.append('}');
        return json.toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> mapValue) {
            return serializeToJson(mapValue);
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder array = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    array.append(',');
                }
                first = false;
                array.append(toJsonValue(item));
            }
            array.append(']');
            return array.toString();
        }
        return '\"' + escapeJson(String.valueOf(value)) + '\"';
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private Object normalizeVariableValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    private String resolveVariableType(Object value) {
        if (value instanceof Number) {
            return "Double";
        }
        if (value instanceof Boolean) {
            return "Boolean";
        }
        return "String";
    }
}
