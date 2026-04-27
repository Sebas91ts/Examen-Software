package com.systembpm.system.modules.ai.infrastructure.repository;

import com.systembpm.system.modules.ai.domain.ProcessAiAnalysis;
import com.systembpm.system.modules.ai.domain.ProcessAiAnalysisStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProcessAiAnalysisRepository extends MongoRepository<ProcessAiAnalysis, String> {
    List<ProcessAiAnalysis> findAllByOrderByCreatedAtDesc();

    List<ProcessAiAnalysis> findByStatusOrderByCreatedAtDesc(ProcessAiAnalysisStatus status);

    Optional<ProcessAiAnalysis> findTopByProcessIdAndFingerprintAndCreatedAtAfter(
            String processId,
            String fingerprint,
            LocalDateTime createdAt);

    Optional<ProcessAiAnalysis> findTopByProcessIdOrderByCreatedAtDesc(String processId);
}
