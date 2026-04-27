package com.systembpm.system.modules.ai.application.dto;

import com.systembpm.system.modules.process.domain.Proceso;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessAiSuggestionActionResponseDto {
    private Proceso process;
    private ProcessAnalysisSuggestionDto suggestion;
}
