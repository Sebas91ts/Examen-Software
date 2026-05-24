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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "document_metadata")
@CompoundIndexes({
        @CompoundIndex(name = "idx_document_process_version", def = "{'tenantId': 1, 'processInstanceId': 1, 'originalName': 1, 'version': -1}"),
        @CompoundIndex(name = "idx_document_process_list", def = "{'tenantId': 1, 'processInstanceId': 1, 'uploadedAt': -1}"),
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
}
