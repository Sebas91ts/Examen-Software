package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.AnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AssistantRequestDto;
import com.systembpm.system.modules.ai.application.dto.EditDiagramRequestDto;
import com.systembpm.system.modules.ai.application.dto.DiagramRequestDto;
import com.systembpm.system.modules.ai.application.dto.FormFillRequestDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisStatusUpdateDto;

public interface IAiService {

    ApiResponse<?> assistant(AssistantRequestDto request);

    ApiResponse<?> analyze(AnalysisRequestDto request);

    ApiResponse<?> generateDiagram(DiagramRequestDto request);

    ApiResponse<?> editDiagram(EditDiagramRequestDto request);

    ApiResponse<?> fillForm(FormFillRequestDto request);

    ApiResponse<?> analyzeProcess(ProcessAnalysisRequestDto request);

    ApiResponse<?> listProcessAnalyses();

    ApiResponse<?> updateProcessAnalysisStatus(String id, ProcessAnalysisStatusUpdateDto request, String reviewedBy);

    ApiResponse<?> applySuggestion(String suggestionId, String reviewedBy);

    ApiResponse<?> rejectSuggestion(String suggestionId, String reviewedBy);
}
