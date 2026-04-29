package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormFillSuggestionDto {
    private String fieldName;
    private Object value;
    private String rationale;
}
