package com.systembpm.system.modules.form.infrastructure.repository;

import com.systembpm.system.modules.form.domain.FormDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionRepository extends MongoRepository<FormDefinition, String> {
    boolean existsByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
            String processKey,
            Integer processVersion,
            String taskDefinitionKey);

    Optional<FormDefinition> findByProcessKeyIgnoreCaseAndProcessVersionAndTaskDefinitionKeyIgnoreCase(
            String processKey,
            Integer processVersion,
            String taskDefinitionKey);

    List<FormDefinition> findByProcessKeyIgnoreCaseOrderByProcessVersionAsc(String processKey);
}
