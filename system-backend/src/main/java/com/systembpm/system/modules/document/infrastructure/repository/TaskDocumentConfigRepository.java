package com.systembpm.system.modules.document.infrastructure.repository;

import com.systembpm.system.modules.document.domain.TaskDocumentConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TaskDocumentConfigRepository extends MongoRepository<TaskDocumentConfig, String> {

    Optional<TaskDocumentConfig> findByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
            String tenantId,
            String processKey,
            Integer processVersion,
            String taskDefinitionKey
    );

    boolean existsByTenantIdAndProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
            String tenantId,
            String processKey,
            Integer processVersion,
            String taskDefinitionKey
    );

    List<TaskDocumentConfig> findByTenantIdAndProcessKeyIgnoreCaseOrderByProcessVersionAsc(
            String tenantId,
            String processKey
    );
}

