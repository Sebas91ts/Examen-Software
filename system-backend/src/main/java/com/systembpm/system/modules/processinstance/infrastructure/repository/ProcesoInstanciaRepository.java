package com.systembpm.system.modules.processinstance.infrastructure.repository;

import com.systembpm.system.modules.processinstance.domain.ProcesoInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProcesoInstanciaRepository extends MongoRepository<ProcesoInstancia, String> {
}
