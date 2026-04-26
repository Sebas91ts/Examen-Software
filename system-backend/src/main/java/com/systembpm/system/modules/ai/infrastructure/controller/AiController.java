package com.systembpm.system.modules.ai.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.AnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AssistantRequestDto;
import com.systembpm.system.modules.ai.application.dto.DiagramRequestDto;
import com.systembpm.system.modules.ai.application.service.IAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final IAiService aiService;

    @PostMapping("/assistant")
    public ResponseEntity<ApiResponse<?>> assistant(@Valid @RequestBody AssistantRequestDto request) {
        log.info("Solicitud POST /api/ai/assistant");
        return ResponseEntity.ok(aiService.assistant(request));
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<?>> analyze(@Valid @RequestBody AnalysisRequestDto request) {
        log.info("Solicitud POST /api/ai/analyze");
        return ResponseEntity.ok(aiService.analyze(request));
    }

    @PostMapping("/generate-diagram")
    public ResponseEntity<ApiResponse<?>> generateDiagram(@Valid @RequestBody DiagramRequestDto request) {
        log.info("Solicitud POST /api/ai/generate-diagram");
        return ResponseEntity.ok(aiService.generateDiagram(request));
    }
}
