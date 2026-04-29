package com.systembpm.system.modules.ai.application.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormFillFieldContextDto {
    private String name;
    private String label;
    private String type;
    private boolean required;
    private String placeholder;
    private String helpText;
    private List<String> options;
}
