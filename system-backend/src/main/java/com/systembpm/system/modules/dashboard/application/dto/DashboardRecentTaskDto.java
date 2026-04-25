package com.systembpm.system.modules.dashboard.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRecentTaskDto {

    private String processInstanceId;
    private String processKey;
    private Integer processVersion;
    private String taskName;
    private String areaNombre;
    private String assignedTo;
    private String completedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
