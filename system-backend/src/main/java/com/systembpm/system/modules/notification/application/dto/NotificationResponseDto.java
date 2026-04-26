package com.systembpm.system.modules.notification.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private String id;
    private String userId;
    private String userEmail;
    private String title;
    private String message;
    private String type;
    private Boolean read;
    private LocalDateTime createdAt;
    private String relatedProcessInstanceId;
    private String relatedTaskId;
}
