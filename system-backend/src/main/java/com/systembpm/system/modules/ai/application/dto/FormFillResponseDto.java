package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormFillResponseDto {
    private String summary;
    private List<FormFillSuggestionDto> suggestions;
}
