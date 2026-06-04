package com.systembpm.system.modules.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task_document_configs")
@CompoundIndex(
        name = "task_document_config_unique",
        def = "{'tenantId': 1, 'processKey': 1, 'processVersion': 1, 'taskDefinitionKey': 1}",
        unique = true
)
public class TaskDocumentConfig {

    @Id
    private String id;

    private String tenantId;

    private String processKey;

    private Integer processVersion;

    private String taskDefinitionKey;

    private Boolean required;

    private Boolean editable;

    private Boolean allowEditing;

    private Boolean collaborativeEditing;

    private Boolean allowVersioning;

    private List<String> allowedMimeTypes;

    private Long maxFileSizeBytes;

    private Integer maxFiles;

    private Boolean readOnlyAfterComplete;

    private Boolean requireApproval;

    private String templateDocumentId;

    private TaskDocumentPermissions permissions;

    private Boolean autoGenerateOnTaskStart;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdBy;

    private String updatedBy;
}
