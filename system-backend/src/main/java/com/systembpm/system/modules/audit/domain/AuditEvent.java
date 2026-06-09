package com.systembpm.system.modules.audit.domain;

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
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_events")
@CompoundIndexes({
        @CompoundIndex(name = "idx_audit_actor_created", def = "{'actorEmail': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_area_created", def = "{'areaId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_entity_created", def = "{'entityType': 1, 'entityId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_document_created", def = "{'documentId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_process_instance_created", def = "{'processInstanceId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_task_created", def = "{'taskInstanceId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_audit_action_created", def = "{'action': 1, 'createdAt': -1}")
})
public class AuditEvent {

    @Id
    private String id;

    @Indexed
    private AuditAction action;

    @Indexed
    private AuditEntityType entityType;

    private String entityId;
    private String entityName;

    private String actorId;
    @Indexed
    private String actorEmail;
    private String actorName;
    private List<String> actorRoles;

    @Indexed
    private String tenantId;
    @Indexed
    private String areaId;
    private String areaName;

    private String processKey;
    private Integer processVersion;
    @Indexed
    private String processInstanceId;
    private String taskDefinitionKey;
    @Indexed
    private String taskInstanceId;
    private String taskName;

    @Indexed
    private String documentId;
    private String documentName;
    private Integer documentVersion;
    private String documentState;
    private String documentRequirementId;
    private String documentRequirementName;

    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private Map<String, Object> metadata;

    @Indexed
    private Instant createdAt;
}
