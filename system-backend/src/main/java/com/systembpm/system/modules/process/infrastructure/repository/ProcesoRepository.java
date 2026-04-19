package com.systembpm.system.modules.process.infrastructure.repository;

import com.systembpm.system.modules.process.domain.Proceso;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio Mongo para procesos BPMN.
 */
public interface ProcesoRepository extends MongoRepository<Proceso, String> {
    Optional<Proceso> findByNombreIgnoreCase(String nombre);
}
