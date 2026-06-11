package com.systembpm.system.modules.document.infrastructure.repository;

import com.systembpm.system.modules.document.domain.DocumentMetadata;
import com.systembpm.system.modules.document.domain.DocumentLifecycleState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends MongoRepository<DocumentMetadata, String> {

    Optional<DocumentMetadata> findTopByTenantIdAndProcessInstanceIdAndOriginalNameOrderByVersionDesc(
            String tenantId,
            String processInstanceId,
            String originalName
    );

    Optional<DocumentMetadata> findByIdAndTenantId(String id, String tenantId);

    List<DocumentMetadata> findByTenantIdAndProcessInstanceIdOrderByUploadedAtDesc(String tenantId, String processInstanceId);

    List<DocumentMetadata> findByProcessInstanceIdOrderByUploadedAtDesc(String processInstanceId);

    List<DocumentMetadata> findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
            String tenantId,
            String processInstanceId,
            String taskDefinitionKey
    );

    List<DocumentMetadata> findByProcessInstanceIdAndTaskDefinitionKeyOrderByUploadedAtDesc(
            String processInstanceId,
            String taskDefinitionKey
    );

    List<DocumentMetadata> findByTenantIdAndProcessInstanceIdAndTaskDefinitionKeyAndTaskInstanceIdOrderByUploadedAtDesc(
            String tenantId,
            String processInstanceId,
            String taskDefinitionKey,
            String taskInstanceId
    );

    List<DocumentMetadata> findByProcessInstanceIdAndTaskDefinitionKeyAndTaskInstanceIdOrderByUploadedAtDesc(
            String processInstanceId,
            String taskDefinitionKey,
            String taskInstanceId
    );

    List<DocumentMetadata> findByTenantIdAndProcessInstanceIdAndDocumentStateInOrderByUpdatedAtDesc(
            String tenantId,
            String processInstanceId,
            List<DocumentLifecycleState> states
    );

    List<DocumentMetadata> findByTenantIdAndDocumentStateInOrderByUpdatedAtDesc(
            String tenantId,
            List<DocumentLifecycleState> states
    );

    List<DocumentMetadata> findByProcessInstanceIdAndDocumentStateInOrderByUpdatedAtDesc(
            String processInstanceId,
            List<DocumentLifecycleState> states
    );

    List<DocumentMetadata> findByDocumentStateInOrderByUpdatedAtDesc(List<DocumentLifecycleState> states);

    List<DocumentMetadata> findByTenantIdAndFolderIdOrderByUploadedAtDesc(String tenantId, String folderId);

    List<DocumentMetadata> findByFolderIdOrderByUploadedAtDesc(String folderId);

    Optional<DocumentMetadata> findByOnlyOfficeDocumentKey(String onlyOfficeDocumentKey);
}
