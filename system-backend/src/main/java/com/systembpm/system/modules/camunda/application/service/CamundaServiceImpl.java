package com.systembpm.system.modules.camunda.application.service;

import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CamundaServiceImpl implements CamundaService {

    private final RestTemplate restTemplate;
    private final ProcesoRepository procesoRepository;

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
        if (processKey == null || processKey.isBlank()) {
            throw new IllegalArgumentException("El processKey es obligatorio");
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/process-definition/key/" + processKey + "/start",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of()),
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo el inicio de instancia: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public List<Map<String, Object>> listarTareas() {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la consulta de tareas: " + ex.getResponseBodyAsString(), ex);
        }
    }

    @Override
    public Map<String, Object> completarTarea(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("El taskId es obligatorio");
        }

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    camundaBaseUrl + "/task/" + taskId + "/complete",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of()),
                    new ParameterizedTypeReference<>() {
                    });
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (HttpStatusCodeException ex) {
            throw new IllegalArgumentException("Camunda rechazo la finalizacion de la tarea: " + ex.getResponseBodyAsString(), ex);
        }
    }
}
