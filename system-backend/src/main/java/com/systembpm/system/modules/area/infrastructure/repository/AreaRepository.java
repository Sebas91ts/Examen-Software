package com.systembpm.system.modules.area.infrastructure.repository;

import com.systembpm.system.modules.area.domain.Area;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AreaRepository extends MongoRepository<Area, String> {

    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Area> findByNombreIgnoreCase(String nombre);

    List<Area> findByActivaTrue();
}
