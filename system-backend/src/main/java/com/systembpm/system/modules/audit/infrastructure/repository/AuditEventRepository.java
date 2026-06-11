package com.systembpm.system.modules.audit.infrastructure.repository;

import com.systembpm.system.modules.audit.domain.AuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    List<AuditEvent> findByDocumentIdOrderByCreatedAtDesc(String documentId);

    List<AuditEvent> findByProcessInstanceIdOrderByCreatedAtDesc(String processInstanceId);

    List<AuditEvent> findByTaskInstanceIdOrderByCreatedAtDesc(String taskInstanceId);
}
