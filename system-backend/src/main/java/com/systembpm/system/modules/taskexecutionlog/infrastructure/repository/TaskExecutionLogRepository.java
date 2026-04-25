package com.systembpm.system.modules.taskexecutionlog.infrastructure.repository;

import com.systembpm.system.modules.taskexecutionlog.domain.TaskExecutionLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TaskExecutionLogRepository extends MongoRepository<TaskExecutionLog, String> {

    List<TaskExecutionLog> findByProcessInstanceIdOrderByCompletedAtAscCreatedAtAsc(String processInstanceId);
}
