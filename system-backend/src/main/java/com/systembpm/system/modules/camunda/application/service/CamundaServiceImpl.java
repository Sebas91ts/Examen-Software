package com.systembpm.system.modules.camunda.application.service;

import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;

@Slf4j
@Service
@RequiredArgsConstructor
public class CamundaServiceImpl implements CamundaService {

    private final RestTemplate restTemplate;
    private final ProcesoRepository procesoRepository;
    private final AreaRepository areaRepository;
    private final AuthService authService;

    @Value("${camunda.base-url}")
    private String camundaBaseUrl;

    @Override
    public Map<String, Object> desplegarProceso(String procesoId) {
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new IllegalArgumentException("Proceso no encontrado con ID: " + procesoId));

        if (proceso.getXml() == null || proceso.getXml().isBlank()) {
            throw new IllegalArgumentException("El proceso no contiene XML BPMN valido");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("bpmn-" + proceso.getId(), ".bpmn");
            Files.writeString(tempFile, proceso.getXml(), StandardCharsets.UTF_8);

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

        try {
            Map<String, Object> body = businessKey == null || businessKey.isBlank()
                    ? Map.of()
                    : Map.of("businessKey", businessKey);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/process-definition/key/" + processKey + "/start",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo el inicio de instancia: " + ex.getResponseBodyAsString(), ex);
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
    public Map<String, Object> completarTarea(String taskId) {
        return completarTarea(taskId, Map.of());
    }

    @Override
    public Map<String, Object> completarTarea(String taskId, Map<String, Object> variables) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }

        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            Map<String, Object> normalizedVariables = normalizeVariables(variables);
            if (!normalizedVariables.isEmpty()) {
                payload.put("variables", normalizedVariables);
            }

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId + "/complete",
                    HttpMethod.POST,
                    new HttpEntity<>(payload),
                    new ParameterizedTypeReference<>() {
                    });
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

    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
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

            Map<String, Object> camundaVariable = new java.util.LinkedHashMap<>();
            camundaVariable.put("value", normalizeVariableValue(value));
            camundaVariable.put("type", resolveVariableType(value));
            normalized.put(key, camundaVariable);
        }
        return normalized;
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
