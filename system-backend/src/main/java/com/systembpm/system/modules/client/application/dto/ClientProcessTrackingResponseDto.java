package com.systembpm.system.modules.client.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProcessTrackingResponseDto {
    private String processName;
    private String estado;
    private LocalDateTime startedAt;
    private Integer progressPercentage;
    private String currentTaskName;
    private String currentAreaName;
    private List<ClientTrackingHistoryItemDto> history;
    private String xmlBpmn;
    private List<String> completedTaskKeys;
    private List<String> activeTaskKeys;
    private List<String> pendingTaskKeys;
}
