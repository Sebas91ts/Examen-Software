package com.systembpm.system.modules.document.infrastructure.repository;

import com.systembpm.system.modules.document.domain.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {

    List<Folder> findByTenantIdAndActiveTrueOrderByNameAsc(String tenantId);

    List<Folder> findByActiveTrueOrderByNameAsc();

    List<Folder> findByTenantIdAndParentFolderIdAndActiveTrueOrderByNameAsc(String tenantId, String parentFolderId);

    List<Folder> findByTenantIdAndParentFolderIdIsNullAndActiveTrueOrderByNameAsc(String tenantId);

    Optional<Folder> findByIdAndTenantIdAndActiveTrue(String id, String tenantId);

    Optional<Folder> findByIdAndActiveTrue(String id);

    boolean existsByIdAndTenantIdAndActiveTrue(String id, String tenantId);
}
