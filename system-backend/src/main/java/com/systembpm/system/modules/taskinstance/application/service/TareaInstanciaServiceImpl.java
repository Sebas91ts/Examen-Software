package com.systembpm.system.modules.taskinstance.application.service;

import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import com.systembpm.system.modules.notification.application.service.NotificationServiceImpl;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.taskinstance.application.dto.TareaInstanciaResponseDto;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;

import org.xml.sax.InputSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class TareaInstanciaServiceImpl implements ITareaInstanciaService {

    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String CUSTOM_NAMESPACE = "http://systembpm.com/schema";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";

    private final TareaInstanciaRepository tareaInstanciaRepository;
    private final AreaRepository areaRepository;
    private final NotificationServiceImpl notificationService;
    private final IRealtimeEventService realtimeEventService;

    @Override
    public TareaInstanciaResponseDto crearPrimeraTareaDesdeInstancia(
            String processInstanceId,
            String processDefinitionId,
            String nombreProceso,
            String xmlProceso) {
        log.info("Creando primera tarea para instancia BPMN: {}", processInstanceId);

        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la instancia es obligatorio");
        }

        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la definicion es obligatorio");
        }

        if (nombreProceso == null || nombreProceso.isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso es obligatorio");
        }

        if (xmlProceso == null || xmlProceso.isBlank()) {
            throw new IllegalArgumentException("La instancia de proceso es obligatoria");
        }

        NodoInicial nodoInicial = extraerPrimerNodoTarea(xmlProceso);
        LaneAreaInfo laneAreaInfo = resolverLaneYArea(xmlProceso, nodoInicial.taskId);

        TareaInstancia tarea = TareaInstancia.builder()
                .processInstanceId(processInstanceId)
                .processDefinitionId(processDefinitionId)
                .nombreProceso(nombreProceso)
                .taskDefinitionKey(nodoInicial.taskId)
                .nombreTarea(nodoInicial.taskName)
                .areaId(laneAreaInfo.areaId)
                .areaNombre(laneAreaInfo.areaNombre)
                .estado(ESTADO_PENDIENTE)
                .assignedTo(null)
                .createdAt(LocalDateTime.now())
                .completedAt(null)
                .build();

        TareaInstancia guardada = tareaInstanciaRepository.save(tarea);
        notificationService.notifyTaskAvailableForArea(
                guardada.getAreaId(),
                guardada.getAreaNombre(),
                guardada.getProcessInstanceId(),
                guardada.getId(),
                guardada.getNombreTarea());
        realtimeEventService.publishTaskAvailableForArea(
                guardada.getAreaId(),
                guardada.getProcessInstanceId(),
                guardada.getId(),
                guardada.getNombreTarea());
        log.info("Tarea inicial creada exitosamente con ID: {}", guardada.getId());

        return mapToResponseDto(guardada);
    }

    @Override
    public List<TareaInstanciaResponseDto> listar() {
        return tareaInstanciaRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public List<TareaInstanciaResponseDto> listarPendientes() {
        return tareaInstanciaRepository.findByEstadoIgnoreCaseOrderByCreatedAtAsc(ESTADO_PENDIENTE).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public List<TareaInstanciaResponseDto> listarPorInstancia(String processInstanceId) {
        return tareaInstanciaRepository.findByProcessInstanceIdOrderByCreatedAtAsc(processInstanceId).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public List<TareaInstanciaResponseDto> listarPorArea(String areaId) {
        if (areaId == null || areaId.isBlank()) {
            throw new IllegalArgumentException("El areaId es obligatorio");
        }

        return tareaInstanciaRepository.findByAreaIdIgnoreCaseOrderByCreatedAtAsc(areaId.trim()).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public List<TareaInstanciaResponseDto> listarPorUsuario(String assignedTo) {
        if (assignedTo == null || assignedTo.isBlank()) {
            throw new IllegalArgumentException("assignedTo es obligatorio");
        }

        return tareaInstanciaRepository.findByAssignedToIgnoreCaseOrderByCreatedAtAsc(assignedTo.trim()).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public List<TareaInstanciaResponseDto> listarPorProceso(String nombreProceso) {
        if (nombreProceso == null || nombreProceso.isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso es obligatorio");
        }

        return tareaInstanciaRepository.findByNombreProcesoIgnoreCaseOrderByCreatedAtAsc(nombreProceso.trim()).stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public TareaInstanciaResponseDto obtenerPorId(String id) {
        TareaInstancia tarea = tareaInstanciaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarea de instancia no encontrada con ID: " + id));

        return mapToResponseDto(tarea);
    }

    private NodoInicial extraerPrimerNodoTarea(String xml) {
        Document document = parseDocument(xml);

        String startEventId = obtenerStartEventId(document);
        String nextNodeId = encontrarSiguienteNodoDesdeStart(document, startEventId);
        Element nextNode = encontrarElementoPorId(document, nextNodeId);

        if (nextNode == null) {
            throw new IllegalArgumentException("No se encontro el nodo siguiente al startEvent");
        }

        String localName = nextNode.getLocalName();
        if (localName == null) {
            throw new IllegalArgumentException("No se pudo determinar el tipo del nodo inicial");
        }

        if (!esNodoTarea(localName)) {
            throw new IllegalArgumentException("El primer nodo posterior al startEvent no es una tarea BPMN");
        }

        String taskId = nextNode.getAttribute("id");
        String taskName = nextNode.getAttribute("name");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("La tarea inicial no tiene un id valido");
        }

        if (taskName == null || taskName.isBlank()) {
            taskName = taskId;
        }

        return new NodoInicial(taskId, taskName);
    }

    private LaneAreaInfo resolverLaneYArea(String xml, String taskId) {
        Document document = parseDocument(xml);
        NodeList laneNodes = document.getElementsByTagNameNS(BPMN_NAMESPACE, "lane");

        for (int i = 0; i < laneNodes.getLength(); i++) {
            Element laneElement = (Element) laneNodes.item(i);
            if (!laneContieneTarea(laneElement, taskId)) {
                continue;
            }

            String areaId = leerAreaIdDeLane(laneElement);
            if (areaId == null || areaId.isBlank()) {
                throw new IllegalArgumentException("La lane de la primera tarea no tiene un area asignada");
            }

            Area area = areaRepository.findById(areaId)
                    .orElseThrow(() -> new IllegalArgumentException("El area asociada a la lane no existe"));

            if (area.getActiva() == null || !area.getActiva()) {
                throw new IllegalArgumentException("El area asociada a la lane esta inactiva");
            }

            return new LaneAreaInfo(area.getId(), area.getNombre());
        }

        throw new IllegalArgumentException("No se encontro una lane asociada a la primera tarea");
    }

    private boolean laneContieneTarea(Element laneElement, String taskId) {
        NodeList flowNodeRefs = laneElement.getElementsByTagNameNS(BPMN_NAMESPACE, "flowNodeRef");
        for (int i = 0; i < flowNodeRefs.getLength(); i++) {
            String ref = flowNodeRefs.item(i).getTextContent();
            if (taskId.equals(ref != null ? ref.trim() : null)) {
                return true;
            }
        }
        return false;
    }

    private String leerAreaIdDeLane(Element laneElement) {
        NodeList areaRefs = laneElement.getElementsByTagNameNS(CUSTOM_NAMESPACE, "areaRef");
        if (areaRefs.getLength() == 0) {
            return null;
        }

        String value = areaRefs.item(0).getTextContent();
        return value != null ? value.trim() : null;
    }

    private String encontrarSiguienteNodoDesdeStart(Document document, String startEventId) {
        NodeList sequenceFlows = document.getElementsByTagNameNS(BPMN_NAMESPACE, "sequenceFlow");

        for (int i = 0; i < sequenceFlows.getLength(); i++) {
            Element sequenceFlow = (Element) sequenceFlows.item(i);
            String sourceRef = sequenceFlow.getAttribute("sourceRef");
            if (!startEventId.equals(sourceRef)) {
                continue;
            }

            String targetRef = sequenceFlow.getAttribute("targetRef");
            if (targetRef != null && !targetRef.isBlank()) {
                return targetRef.trim();
            }
        }

        throw new IllegalArgumentException("No se encontro un sequenceFlow saliendo del startEvent");
    }

    private String obtenerStartEventId(Document document) {
        NodeList startEvents = document.getElementsByTagNameNS(BPMN_NAMESPACE, "startEvent");
        if (startEvents.getLength() == 0) {
            throw new IllegalArgumentException("No se encontro ningun bpmn:startEvent en la definicion BPMN");
        }

        Element startEvent = (Element) startEvents.item(0);
        String startEventId = startEvent.getAttribute("id");
        if (startEventId == null || startEventId.isBlank()) {
            throw new IllegalArgumentException("El startEvent inicial no tiene un id valido");
        }

        return startEventId.trim();
    }

    private Element encontrarElementoPorId(Document document, String elementId) {
        NodeList allNodes = document.getElementsByTagNameNS(BPMN_NAMESPACE, "*");
        for (int i = 0; i < allNodes.getLength(); i++) {
            Element element = (Element) allNodes.item(i);
            if (elementId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    private boolean esNodoTarea(String localName) {
        return "task".equals(localName)
                || "userTask".equals(localName)
                || localName.endsWith("Task");
    }

    private Document parseDocument(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("El XML BPMN no puede estar vacio");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo analizar el XML BPMN", ex);
        }
    }

    private TareaInstanciaResponseDto mapToResponseDto(TareaInstancia tarea) {
        return TareaInstanciaResponseDto.builder()
                .id(tarea.getId())
                .processInstanceId(tarea.getProcessInstanceId())
                .processDefinitionId(tarea.getProcessDefinitionId())
                .nombreProceso(tarea.getNombreProceso())
                .taskDefinitionKey(tarea.getTaskDefinitionKey())
                .nombreTarea(tarea.getNombreTarea())
                .areaId(tarea.getAreaId())
                .areaNombre(tarea.getAreaNombre())
                .estado(tarea.getEstado())
                .assignedTo(tarea.getAssignedTo())
                .createdAt(tarea.getCreatedAt())
                .completedAt(tarea.getCompletedAt())
                .build();
    }

    private record NodoInicial(String taskId, String taskName) {
    }

    private record LaneAreaInfo(String areaId, String areaNombre) {
    }
}
