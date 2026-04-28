package com.systembpm.system.modules.client.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTaskCompleteResponseDto {
    private String taskId;
    private String processInstanceId;
    private String processName;
    private String completedBy;
    private LocalDateTime completedAt;
    private String message;
}
