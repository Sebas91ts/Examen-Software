package com.systembpm.system.modules.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBusinessContextPayloadDto {
    private String message;
    private Map<String, Object> user;
    private Map<String, Object> currentContext;
    private List<Map<String, Object>> processes;
    private List<Map<String, Object>> tasks;
    private List<Map<String, Object>> documents;
    private List<Map<String, Object>> forms;
    private List<Map<String, Object>> areas;
    private Map<String, Object> limits;
}
