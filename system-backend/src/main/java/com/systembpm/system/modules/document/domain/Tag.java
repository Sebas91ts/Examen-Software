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
@Document(collection = "document_tags")
@CompoundIndexes({
        @CompoundIndex(name = "idx_tag_tenant_active_name", def = "{'tenantId': 1, 'active': 1, 'name': 1}")
})
public class Tag {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    private String name;

    private String color;

    private String description;

    @Indexed
    private Boolean active;

    private Instant createdAt;

    private Instant updatedAt;

    private String createdBy;

    private String updatedBy;
}
