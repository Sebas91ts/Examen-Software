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
public class ClientProcessInstanceListItemDto {
    private String id;
    private String processName;
    private String processKey;
    private Integer processVersion;
    private String processInstanceId;
    private String estado;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
