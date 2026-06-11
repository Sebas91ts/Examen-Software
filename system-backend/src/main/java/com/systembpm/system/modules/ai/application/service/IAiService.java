package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.AnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AiBusinessContextRequestDto;
import com.systembpm.system.modules.ai.application.dto.AiDocumentAnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AiVoiceRequestDto;
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

    ApiResponse<?> assist(AiBusinessContextRequestDto request, String requesterEmail);

    ApiResponse<?> recommendProcess(AiBusinessContextRequestDto request, String requesterEmail);

    ApiResponse<?> planReport(AiBusinessContextRequestDto request, String requesterEmail);

    ApiResponse<?> analyzeDocument(AiDocumentAnalysisRequestDto request, String requesterEmail);

    ApiResponse<?> context(AiBusinessContextRequestDto request, String requesterEmail);

    ApiResponse<?> voice(AiVoiceRequestDto request, String requesterEmail);

    ApiResponse<?> predictTaskRisk(String taskId, String requesterEmail);

    ApiResponse<?> predictInstanceRisk(String processInstanceId, String requesterEmail);

    ApiResponse<?> recommendAssignment(String taskId, String requesterEmail);

    ApiResponse<?> intelligentRoutingDashboard(String requesterEmail);

    ApiResponse<?> analyzeProcess(ProcessAnalysisRequestDto request);

    ApiResponse<?> listProcessAnalyses();

    ApiResponse<?> updateProcessAnalysisStatus(String id, ProcessAnalysisStatusUpdateDto request, String reviewedBy);

    ApiResponse<?> applySuggestion(String suggestionId, String reviewedBy);

    ApiResponse<?> rejectSuggestion(String suggestionId, String reviewedBy);
}
