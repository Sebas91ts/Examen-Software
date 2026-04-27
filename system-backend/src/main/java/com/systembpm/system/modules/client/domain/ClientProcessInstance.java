package com.systembpm.system.modules.client.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "client_process_instances")
public class ClientProcessInstance {

    @Id
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
