package com.systembpm.system.modules.ai.infrastructure.repository;

import com.systembpm.system.modules.ai.domain.ProcessAiAnalysisSuggestion;
import com.systembpm.system.modules.ai.domain.ProcessAiSuggestionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcessAiSuggestionRepository extends MongoRepository<ProcessAiAnalysisSuggestion, String> {
    List<ProcessAiAnalysisSuggestion> findByAnalysisIdOrderByCreatedAtAsc(String analysisId);

    List<ProcessAiAnalysisSuggestion> findByStatusOrderByCreatedAtDesc(ProcessAiSuggestionStatus status);
}
