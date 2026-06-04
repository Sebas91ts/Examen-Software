package com.systembpm.system.modules.document.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "document_metadata")
@CompoundIndexes({
        @CompoundIndex(name = "idx_document_process_version", def = "{'tenantId': 1, 'processInstanceId': 1, 'originalName': 1, 'version': -1}"),
        @CompoundIndex(name = "idx_document_process_list", def = "{'tenantId': 1, 'processInstanceId': 1, 'uploadedAt': -1}"),
        @CompoundIndex(name = "idx_document_task_list", def = "{'tenantId': 1, 'processInstanceId': 1, 'taskDefinitionKey': 1, 'taskInstanceId': 1, 'uploadedAt': -1}"),
        @CompoundIndex(name = "idx_document_pending", def = "{'tenantId': 1, 'processInstanceId': 1, 'documentState': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_document_folder_list", def = "{'tenantId': 1, 'folderId': 1, 'uploadedAt': -1}"),
        @CompoundIndex(name = "idx_document_enterprise_search", def = "{'tenantId': 1, 'folderId': 1, 'tagIds': 1, 'documentState': 1, 'processKey': 1, 'taskDefinitionKey': 1, 'uploadedAt': -1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_document_editor_session", def = "{'tenantId': 1, 'onlyOfficeDocumentKey': 1, 'currentEditor': 1, 'editingStartedAt': -1}"),
        @CompoundIndex(name = "idx_document_s3_key", def = "{'s3Key': 1}", unique = true)
})
public class DocumentMetadata {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String processInstanceId;

    private String fileName;

    @Indexed
    private String originalName;

    private String mimeType;

    private Long size;

    private String s3Key;

    private String uploadedBy;

    @Indexed
    private Instant uploadedAt;

    private Integer version;

    private DocumentStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant lastAccessedAt;

    private String updatedBy;

    private String processKey;

    private Integer processVersion;

    private String taskDefinitionKey;

    private String taskInstanceId;

    private DocumentLifecycleState documentState;

    private Boolean locked;

    private String lockedBy;

    private Instant lockedAt;

    private String approvedBy;

    private Instant approvedAt;

    private String rejectedBy;

    private Instant rejectedAt;

    private List<String> comments;

    @Indexed
    private String folderId;

    @Indexed
    private List<String> tagIds;

    private Boolean editable;

    private Boolean collaborativeEditing;

    @Indexed
    private String onlyOfficeDocumentKey;

    private String templateDocumentId;

    private String currentEditor;

    private Instant editingStartedAt;
}
