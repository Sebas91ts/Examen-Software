package com.systembpm.system.modules.ai.infrastructure.controller;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.AnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AssistantRequestDto;
import com.systembpm.system.modules.ai.application.dto.EditDiagramRequestDto;
import com.systembpm.system.modules.ai.application.dto.DiagramRequestDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisStatusUpdateDto;
import com.systembpm.system.modules.ai.application.service.IAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @PostMapping("/edit-diagram")
    public ResponseEntity<ApiResponse<?>> editDiagram(@Valid @RequestBody EditDiagramRequestDto request) {
        log.info("Solicitud POST /api/ai/edit-diagram");
        return ResponseEntity.ok(aiService.editDiagram(request));
    }

    @PostMapping("/analyze-process")
    public ResponseEntity<ApiResponse<?>> analyzeProcess(@Valid @RequestBody ProcessAnalysisRequestDto request) {
        log.info("Solicitud POST /api/ai/analyze-process");
        return ResponseEntity.ok(aiService.analyzeProcess(request));
    }

    @GetMapping("/analyses")
    public ResponseEntity<ApiResponse<?>> listProcessAnalyses() {
        log.info("Solicitud GET /api/ai/analyses");
        return ResponseEntity.ok(aiService.listProcessAnalyses());
    }

    @PatchMapping("/analyses/{id}/status")
    public ResponseEntity<ApiResponse<?>> updateProcessAnalysisStatus(
            @PathVariable String id,
            @Valid @RequestBody ProcessAnalysisStatusUpdateDto request,
            Authentication authentication) {
        log.info("Solicitud PATCH /api/ai/analyses/{}/status", id);
        String reviewedBy = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(aiService.updateProcessAnalysisStatus(id, request, reviewedBy));
    }

    @PostMapping("/suggestions/{id}/apply")
    public ResponseEntity<ApiResponse<?>> applySuggestion(
            @PathVariable String id,
            Authentication authentication) {
        log.info("Solicitud POST /api/ai/suggestions/{}/apply", id);
        String reviewedBy = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(aiService.applySuggestion(id, reviewedBy));
    }

    @PostMapping("/suggestions/{id}/reject")
    public ResponseEntity<ApiResponse<?>> rejectSuggestion(
            @PathVariable String id,
            Authentication authentication) {
        log.info("Solicitud POST /api/ai/suggestions/{}/reject", id);
        String reviewedBy = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(aiService.rejectSuggestion(id, reviewedBy));
    }
}
