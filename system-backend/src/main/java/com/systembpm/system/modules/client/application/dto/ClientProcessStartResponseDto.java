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
public class ClientProcessStartResponseDto {
    private String id;
    private String clientUserId;
    private String clientEmail;
    private String processId;
    private String processKey;
    private Integer processVersion;
    private String processName;
    private String processInstanceId;
    private String estado;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
