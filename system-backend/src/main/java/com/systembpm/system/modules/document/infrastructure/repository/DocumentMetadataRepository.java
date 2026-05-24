package com.systembpm.system.modules.document.infrastructure.repository;

import com.systembpm.system.modules.document.domain.DocumentMetadata;
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
}
