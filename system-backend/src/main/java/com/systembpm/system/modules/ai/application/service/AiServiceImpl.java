package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.*;
import com.systembpm.system.modules.ai.infrastructure.client.FastApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements IAiService {

    private final FastApiClient fastApiClient;

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
        return execute("/ai/generate-diagram", request, DiagramResponseDto.class, "generate diagram");
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
            return ApiResponse.error("No se pudo comunicar con el servicio de IA");
        } catch (Exception ex) {
            log.error("Error inesperado en operacion {}", operationName, ex);
            return ApiResponse.error("Ocurrio un error inesperado al procesar la solicitud de IA");
        }
    }
}
