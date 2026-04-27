package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.*;
import com.systembpm.system.modules.ai.infrastructure.client.FastApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements IAiService {

    private final FastApiClient fastApiClient;
    private final BpmnGeneratorService bpmnGeneratorService;
    private final ProcessAiAnalysisService processAiAnalysisService;

    @Override
    public ApiResponse<?> assistant(AssistantRequestDto request) {
        return execute("/ai/assistant", request, AssistantResponseDto.class, "assistant");
    }

    @Override
    public ApiResponse<?> analyze(AnalysisRequestDto request) {
        return execute("/ai/analyze", request, AnalysisResponseDto.class, "analyze");
    }

    @Override
    public ApiResponse<?> generateDiagram(DiagramRequestDto request) {
        ApiResponse<DiagramResponseDto> fastApiResponse = execute(
                "/ai/generate-diagram",
                request,
                DiagramResponseDto.class,
                "generate diagram");

        if (!fastApiResponse.isSuccess() || fastApiResponse.getData() == null) {
            return ApiResponse.error(fastApiResponse.getMessage());
        }

        BpmnGenerationResponseDto generated = bpmnGeneratorService.generate(
                fastApiResponse.getData(),
                null);

        return ApiResponse.success("Diagrama BPMN generado exitosamente", generated);
    }

    @Override
    public ApiResponse<?> editDiagram(EditDiagramRequestDto request) {
        ApiResponse<EditDiagramResponseDto> response = execute("/ai/edit-diagram", request, EditDiagramResponseDto.class, "edit diagram");
        if (!response.isSuccess() || response.getData() == null) {
            return response;
        }

        log.info("Diagrama editado recibido desde FastAPI. xmlLength={}", response.getData().getXml() == null ? 0 : response.getData().getXml().length());
        return response;
    }

    @Override
    public ApiResponse<?> analyzeProcess(ProcessAnalysisRequestDto request) {
        try {
            return ApiResponse.success(
                    "Analisis IA generado correctamente.",
                    processAiAnalysisService.analyzeManual(request));
        } catch (RuntimeException ex) {
            log.error("Error procesando analisis IA del proceso", ex);
            return ApiResponse.error("No se pudo analizar el proceso con IA. Intenta nuevamente en unos minutos.");
        }
    }

    @Override
    public ApiResponse<?> listProcessAnalyses() {
        return ApiResponse.success(
                "Analisis IA listados correctamente.",
                processAiAnalysisService.listAnalyses());
    }

    @Override
    public ApiResponse<?> updateProcessAnalysisStatus(
            String id,
            ProcessAnalysisStatusUpdateDto request,
            String reviewedBy) {
        try {
            return ApiResponse.success(
                    "Estado del analisis actualizado correctamente.",
                    processAiAnalysisService.updateStatus(id, request.getStatus(), reviewedBy));
        } catch (RuntimeException ex) {
            log.warn("No se pudo actualizar estado de analisis IA id={}", id, ex);
            return ApiResponse.error("No se pudo actualizar el estado del analisis IA.");
        }
    }

    @Override
    public ApiResponse<?> applySuggestion(String suggestionId, String reviewedBy) {
        try {
            return ApiResponse.success(
                    "Sugerencia aplicada correctamente.",
                    processAiAnalysisService.applySuggestion(suggestionId, reviewedBy));
        } catch (RuntimeException ex) {
            log.warn("No se pudo aplicar sugerencia IA id={}", suggestionId, ex);
            return ApiResponse.error(ex.getMessage() != null ? ex.getMessage() : "No se pudo aplicar la sugerencia IA.");
        }
    }

    @Override
    public ApiResponse<?> rejectSuggestion(String suggestionId, String reviewedBy) {
        try {
            return ApiResponse.success(
                    "Sugerencia rechazada correctamente.",
                    processAiAnalysisService.rejectSuggestion(suggestionId, reviewedBy));
        } catch (RuntimeException ex) {
            log.warn("No se pudo rechazar sugerencia IA id={}", suggestionId, ex);
            return ApiResponse.error(ex.getMessage() != null ? ex.getMessage() : "No se pudo rechazar la sugerencia IA.");
        }
    }

    private <TResponse> ApiResponse<TResponse> execute(
            String path,
            Object request,
            Class<TResponse> responseType,
            String operationName) {

        log.info("Reenviando solicitud AI hacia FastAPI: {}", path);
        try {
            TResponse response = fastApiClient.post(path, request, responseType);
            if (response == null) {
                log.warn("FastAPI retorno respuesta vacia para {}", path);
                return ApiResponse.error("FastAPI no devolvio una respuesta valida");
            }
            return ApiResponse.success("Solicitud procesada correctamente", response);
        } catch (RestClientException ex) {
            log.error("Error consumiendo FastAPI en {}", path, ex);
            String message = ex.getMessage();
            if (message != null && message.contains("503")) {
                return ApiResponse.error("El servicio de IA está ocupado en este momento. Intenta de nuevo en unos segundos.");
            }
            return ApiResponse.error(message != null && !message.isBlank()
                    ? message
                    : "No se pudo comunicar con el servicio de IA");
        } catch (Exception ex) {
            log.error("Error inesperado en operacion {}", operationName, ex);
            return ApiResponse.error("Ocurrio un error inesperado al procesar la solicitud de IA");
        }
    }
}
