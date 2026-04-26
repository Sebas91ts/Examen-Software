package com.systembpm.system.modules.processinstance.application.service;

import com.systembpm.system.modules.camunda.application.service.CamundaService;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.processinstance.application.dto.ProcesoInstanciaResponseDto;
import com.systembpm.system.modules.processinstance.domain.ProcesoInstancia;
import com.systembpm.system.modules.processinstance.infrastructure.repository.ProcesoInstanciaRepository;
import com.systembpm.system.modules.realtime.application.service.IRealtimeEventService;
import com.systembpm.system.modules.taskinstance.application.service.ITareaInstanciaService;
import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import com.systembpm.system.modules.taskexecutionlog.infrastructure.repository.TaskExecutionLogRepository;
import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import com.systembpm.system.modules.taskinstance.infrastructure.repository.TareaInstanciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.xml.sax.InputSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesoInstanciaServiceImpl implements IProcesoInstanciaService {

    private static final String ESTADO_PUBLICADO = "PUBLICADO";
    private static final String ESTADO_ACTIVA = "ACTIVA";
    private static final String INICIADO_POR_DEFAULT = "admin";
    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    private final ProcesoRepository procesoRepository;
    private final ProcesoInstanciaRepository procesoInstanciaRepository;
    private final ITareaInstanciaService tareaInstanciaService;
    private final IRealtimeEventService realtimeEventService;
    private final CamundaService camundaService;
    private final TaskExecutionLogRepository taskExecutionLogRepository;
    private final TareaInstanciaRepository tareaInstanciaRepository;

    @Override
    public ProcesoInstanciaResponseDto iniciarDesdeDefinicion(String processDefinitionId) {
        log.info("Iniciando instancia desde definicion BPMN: {}", processDefinitionId);

        Proceso definicion = procesoRepository.findById(processDefinitionId)
                .orElseThrow(() -> new IllegalArgumentException("La definicion de proceso no existe"));

        validarDefinicionPublicada(definicion);
        String startElementId = extraerStartEventId(definicion.getXml());
        LocalDateTime now = LocalDateTime.now();

        ProcesoInstancia instancia = ProcesoInstancia.builder()
                .processDefinitionId(definicion.getId())
                .processKey(definicion.getProcessKey())
                .version(definicion.getVersion())
                .nombreProceso(definicion.getNombre())
                .estado(ESTADO_ACTIVA)
                .currentElementId(startElementId)
                .iniciadoPor(INICIADO_POR_DEFAULT)
                .startedAt(now)
                .finishedAt(null)
                .build();

        ProcesoInstancia guardada = procesoInstanciaRepository.save(instancia);
        log.info("Instancia BPMN creada exitosamente con ID: {}", guardada.getId());

        tareaInstanciaService.crearPrimeraTareaDesdeInstancia(
                guardada.getId(),
                guardada.getProcessDefinitionId(),
                guardada.getNombreProceso(),
                definicion.getXml());

        realtimeEventService.publishProcessUpdated(
                guardada.getId(),
                "Se inicio una nueva instancia del proceso " + guardada.getNombreProceso() + ".",
                java.util.Map.of(
                        "processKey", guardada.getProcessKey(),
                        "version", guardada.getVersion(),
                        "estado", guardada.getEstado()));

        return mapToResponseDto(guardada);
    }

    @Override
    public List<ProcesoInstanciaResponseDto> listar() {
        log.info("Listando instancias de proceso BPMN desde Camunda");
        List<Map<String, Object>> activeInstances = camundaService.listarInstanciasProcesoActivas();
        List<TaskExecutionLog> logs = taskExecutionLogRepository.findAll();
        List<TareaInstancia> localTasks = tareaInstanciaRepository.findAll();

        return activeInstances.stream()
                .map(instance -> mapToResponseDto(instance, logs, localTasks))
                .toList();
    }

    @Override
    public ProcesoInstanciaResponseDto obtenerPorId(String id) {
        log.info("Buscando instancia de proceso BPMN en Camunda por ID: {}", id);
        Map<String, Object> instance = camundaService.obtenerInstanciaProceso(id);
        return mapToResponseDto(instance, taskExecutionLogRepository.findAll(), tareaInstanciaRepository.findAll());
    }

    private void validarDefinicionPublicada(Proceso definicion) {
        if (definicion.getEstado() == null || !ESTADO_PUBLICADO.equalsIgnoreCase(definicion.getEstado())) {
            throw new IllegalArgumentException("Solo se pueden iniciar instancias desde definiciones PUBLICADAS");
        }
    }

    private String extraerStartEventId(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("La definicion BPMN no contiene XML valido");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList startEvents = document.getElementsByTagNameNS(BPMN_NAMESPACE, "startEvent");

            if (startEvents.getLength() == 0) {
                throw new IllegalArgumentException("No se encontro ningun bpmn:startEvent en la definicion BPMN");
            }

            String startEventId = startEvents.item(0).getAttributes().getNamedItem("id").getNodeValue();
            if (startEventId == null || startEventId.isBlank()) {
                throw new IllegalArgumentException("El startEvent inicial no tiene un id valido");
            }

            return startEventId;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo analizar el XML BPMN de la definicion", ex);
        }
    }

    private ProcesoInstanciaResponseDto mapToResponseDto(ProcesoInstancia instancia) {
        return ProcesoInstanciaResponseDto.builder()
                .id(instancia.getId())
                .processDefinitionId(instancia.getProcessDefinitionId())
                .processKey(instancia.getProcessKey())
                .version(instancia.getVersion())
                .nombreProceso(instancia.getNombreProceso())
                .estado(instancia.getEstado())
                .currentElementId(instancia.getCurrentElementId())
                .iniciadoPor(instancia.getIniciadoPor())
                .startedAt(instancia.getStartedAt())
                .finishedAt(instancia.getFinishedAt())
                .build();
    }

    private ProcesoInstanciaResponseDto mapToResponseDto(
            Map<String, Object> instance,
            List<TaskExecutionLog> logs,
            List<TareaInstancia> localTasks) {
        String processInstanceId = stringValue(instance.get("id"));
        String processDefinitionId = stringValue(instance.get("processDefinitionId"));
        String processKey = stringValue(instance.get("processKey"));
        Integer version = integerValue(instance.get("processVersion"));

        return ProcesoInstanciaResponseDto.builder()
                .id(processInstanceId)
                .processDefinitionId(processDefinitionId)
                .processKey(processKey)
                .version(version)
                .nombreProceso(stringValue(instance.get("nombreProceso")))
                .estado(stringValue(instance.get("estado")))
                .currentElementId(stringValue(instance.get("activityId")))
                .iniciadoPor(resolveStartedBy(processInstanceId, logs))
                .startedAt(resolveStartedAt(processInstanceId, logs, localTasks))
                .finishedAt(null)
                .build();
    }

    private String resolveStartedBy(String processInstanceId, List<TaskExecutionLog> logs) {
        return logs.stream()
                .filter(item -> processInstanceId.equals(item.getProcessInstanceId()))
                .map(TaskExecutionLog::getCompletedBy)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(INICIADO_POR_DEFAULT);
    }

    private LocalDateTime resolveStartedAt(
            String processInstanceId,
            List<TaskExecutionLog> logs,
            List<TareaInstancia> localTasks) {
        LocalDateTime fromLocalTask = localTasks.stream()
                .filter(item -> processInstanceId.equals(item.getProcessInstanceId()))
                .map(TareaInstancia::getCreatedAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        if (fromLocalTask != null) {
            return fromLocalTask;
        }

        LocalDateTime fromHistoryCreation = logs.stream()
                .filter(item -> processInstanceId.equals(item.getProcessInstanceId()))
                .map(item -> item.getCreatedAt() != null ? item.getCreatedAt() : item.getCompletedAt())
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        return fromHistoryCreation;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
