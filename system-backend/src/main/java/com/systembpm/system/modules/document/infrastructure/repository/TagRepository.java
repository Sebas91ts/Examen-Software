package com.systembpm.system.modules.document.infrastructure.repository;

import com.systembpm.system.modules.document.domain.Tag;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends MongoRepository<Tag, String> {

    List<Tag> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    List<Tag> findByTenantIdAndIdInAndActiveTrue(String tenantId, Collection<String> ids);

    Optional<Tag> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    boolean existsByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    boolean existsByTenantIdAndNameIgnoreCaseAndActiveTrue(String tenantId, String name);
}
