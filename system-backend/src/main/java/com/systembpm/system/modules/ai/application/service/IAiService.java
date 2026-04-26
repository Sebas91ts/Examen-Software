package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.common.response.ApiResponse;
import com.systembpm.system.modules.ai.application.dto.AnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.AssistantRequestDto;
import com.systembpm.system.modules.ai.application.dto.EditDiagramRequestDto;
import com.systembpm.system.modules.ai.application.dto.DiagramRequestDto;

public interface IAiService {

    ApiResponse<?> assistant(AssistantRequestDto request);

    ApiResponse<?> analyze(AnalysisRequestDto request);

    ApiResponse<?> generateDiagram(DiagramRequestDto request);

    ApiResponse<?> editDiagram(EditDiagramRequestDto request);
}
