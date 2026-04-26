package com.systembpm.system.modules.processmonitoring.application.dto;

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
public class ProcessMonitorInstanceDto {

    private String processInstanceId;
    private String businessKey;
    private LocalDateTime startedAt;
    private List<String> currentTaskNames;
    private List<String> currentAreas;
    private List<String> assignedUsers;
    private int activeTaskCount;
}
