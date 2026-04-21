package com.systembpm.system.modules.taskinstance.infrastructure.repository;

import com.systembpm.system.modules.taskinstance.domain.TareaInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TareaInstanciaRepository extends MongoRepository<TareaInstancia, String> {

    List<TareaInstancia> findByEstadoIgnoreCaseOrderByCreatedAtAsc(String estado);

    List<TareaInstancia> findByProcessInstanceIdOrderByCreatedAtAsc(String processInstanceId);

    List<TareaInstancia> findByAreaIdIgnoreCaseOrderByCreatedAtAsc(String areaId);

    List<TareaInstancia> findByAssignedToIgnoreCaseOrderByCreatedAtAsc(String assignedTo);

    List<TareaInstancia> findByNombreProcesoIgnoreCaseOrderByCreatedAtAsc(String nombreProceso);
}
