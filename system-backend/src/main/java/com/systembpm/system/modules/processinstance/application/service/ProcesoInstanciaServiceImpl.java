package com.systembpm.system.modules.processinstance.application.service;

import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import com.systembpm.system.modules.processinstance.application.dto.ProcesoInstanciaResponseDto;
import com.systembpm.system.modules.processinstance.domain.ProcesoInstancia;
import com.systembpm.system.modules.processinstance.infrastructure.repository.ProcesoInstanciaRepository;
import com.systembpm.system.modules.taskinstance.application.service.ITareaInstanciaService;
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

        return mapToResponseDto(guardada);
    }

    @Override
    public List<ProcesoInstanciaResponseDto> listar() {
        log.info("Listando instancias de proceso BPMN");
        return procesoInstanciaRepository.findAll().stream()
                .map(this::mapToResponseDto)
                .toList();
    }

    @Override
    public ProcesoInstanciaResponseDto obtenerPorId(String id) {
        log.info("Buscando instancia de proceso BPMN por ID: {}", id);

        ProcesoInstancia instancia = procesoInstanciaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instancia de proceso no encontrada con ID: " + id));

        return mapToResponseDto(instancia);
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
}
