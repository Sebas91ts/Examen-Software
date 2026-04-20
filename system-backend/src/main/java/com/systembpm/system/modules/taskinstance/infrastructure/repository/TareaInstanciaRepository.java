package com.systembpm.system.modules.taskinstance.infrastructure.repository;

import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TareaInstanciaRepository extends MongoRepository<TareaInstancia, String> {

    List<TareaInstancia> findByProcessInstanceIdOrderByCreatedAtAsc(String processInstanceId);
}
