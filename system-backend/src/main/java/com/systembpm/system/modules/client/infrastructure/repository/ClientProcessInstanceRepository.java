package com.systembpm.system.modules.client.infrastructure.repository;

import com.systembpm.system.modules.client.domain.ClientProcessInstance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.List;

public interface ClientProcessInstanceRepository extends MongoRepository<ClientProcessInstance, String> {
    List<ClientProcessInstance> findByClientUserIdOrderByStartedAtDesc(String clientUserId);
    Optional<ClientProcessInstance> findByProcessInstanceIdAndClientEmail(String processInstanceId, String clientEmail);
    Optional<ClientProcessInstance> findByProcessInstanceId(String processInstanceId);
}
