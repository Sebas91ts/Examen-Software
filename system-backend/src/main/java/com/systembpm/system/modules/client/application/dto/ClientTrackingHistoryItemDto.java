package com.systembpm.system.modules.client.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTrackingHistoryItemDto {
    private String taskName;
    private String areaName;
    private LocalDateTime completedAt;
    private Map<String, Object> formData;
}
