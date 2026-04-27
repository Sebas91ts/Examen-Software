package com.systembpm.system.modules.ai.application.service;

import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisIssueDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisRequestDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisResponseDto;
import com.systembpm.system.modules.ai.application.dto.ProcessAnalysisSuggestionDto;
import com.systembpm.system.modules.ai.domain.ProcessAiAnalysis;
import com.systembpm.system.modules.ai.domain.ProcessAiAnalysisIssue;
import com.systembpm.system.modules.ai.domain.ProcessAiAnalysisStatus;
import com.systembpm.system.modules.ai.domain.ProcessAiAnalysisSuggestion;
import com.systembpm.system.modules.ai.infrastructure.client.FastApiClient;
import com.systembpm.system.modules.ai.infrastructure.repository.ProcessAiAnalysisRepository;
import com.systembpm.system.modules.notification.application.service.NotificationServiceImpl;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessAiAnalysisService {

    private static final String FASTAPI_ANALYSIS_PATH = "/ai/analyze-process";
    private static final int RECENT_DUPLICATE_HOURS = 24;

    private final FastApiClient fastApiClient;
    private final ProcessAiAnalysisRepository analysisRepository;
    private final ProcesoRepository procesoRepository;
    private final NotificationServiceImpl notificationService;

    @Value("${ai.analysis.enabled:true}")
    private boolean enabled;

    @Value("${ai.analysis.min-score-notify:70}")
    private int minScoreNotify;

    @Value("${ai.analysis.notify-severity:high}")
    private String notifySeverity;

    public ProcessAnalysisResponseDto analyzeManual(ProcessAnalysisRequestDto request) {
        ProcessAnalysisResponseDto response = requestAnalysis(request);
        ProcessAiAnalysis saved = saveRelevantAnalysis(request, response);
        return toDto(saved);
    }

    public List<ProcessAnalysisResponseDto> listAnalyses() {
        return analysisRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    public ProcessAnalysisResponseDto updateStatus(String id, String status, String reviewedBy) {
        ProcessAiAnalysis analysis = analysisRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El analisis IA no existe"));

        ProcessAiAnalysisStatus nextStatus = ProcessAiAnalysisStatus.valueOf(status.trim().toUpperCase());
        analysis.setStatus(nextStatus);
        analysis.setReviewedAt(LocalDateTime.now());
        analysis.setReviewedBy(reviewedBy);
        return toDto(analysisRepository.save(analysis));
    }

    @Scheduled(fixedDelayString = "${ai.analysis.schedule-ms:21600000}")
    public void runScheduledAnalysis() {
        if (!enabled) {
            return;
        }

        List<Proceso> publishedProcesses = procesoRepository.findByEstadoIgnoreCase("PUBLICADO").stream()
                .sorted(Comparator.comparing(this::lastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        log.info("Analisis IA programado iniciado. procesosPublicados={}", publishedProcesses.size());

        for (Proceso proceso : publishedProcesses) {
            try {
                if (!shouldAnalyze(proceso)) {
                    continue;
                }

                ProcessAnalysisRequestDto request = ProcessAnalysisRequestDto.builder()
                        .processId(proceso.getId())
                        .processKey(proceso.getProcessKey())
                        .processVersion(proceso.getVersion())
                        .processName(proceso.getNombre())
                        .processXml(proceso.getXml())
                        .metrics(Map.of())
                        .build();

                ProcessAnalysisResponseDto response = requestAnalysis(request);
                ProcessAiAnalysis saved = saveRelevantAnalysis(request, response);

                if (shouldNotify(saved)) {
                    notificationService.notifyAiAnalysisForAdmins(
                            saved.getProcessName(),
                            saved.getProcessId(),
                            saved.getId(),
                            buildNotificationMessage(saved));
                }
            } catch (Exception ex) {
                log.warn("No se pudo analizar proceso con IA. processId={} processKey={}",
                        proceso.getId(), proceso.getProcessKey(), ex);
            }
        }
    }

    private ProcessAnalysisResponseDto requestAnalysis(ProcessAnalysisRequestDto request) {
        ProcessAnalysisResponseDto response = fastApiClient.post(
                FASTAPI_ANALYSIS_PATH,
                request,
                ProcessAnalysisResponseDto.class);

        response.setProcessId(firstNonBlank(request.getProcessId(), response.getProcessId()));
        response.setProcessKey(firstNonBlank(request.getProcessKey(), response.getProcessKey()));
        response.setProcessVersion(request.getProcessVersion() != null ? request.getProcessVersion() : response.getProcessVersion());
        response.setProcessName(firstNonBlank(request.getProcessName(), response.getProcessName()));
        return response;
    }

    private ProcessAiAnalysis saveRelevantAnalysis(ProcessAnalysisRequestDto request, ProcessAnalysisResponseDto response) {
        String fingerprint = fingerprint(response);
        LocalDateTime recentThreshold = LocalDateTime.now().minusHours(RECENT_DUPLICATE_HOURS);

        if (request.getProcessId() != null && !request.getProcessId().isBlank()) {
            ProcessAiAnalysis duplicate = analysisRepository
                    .findTopByProcessIdAndFingerprintAndCreatedAtAfter(request.getProcessId(), fingerprint, recentThreshold)
                    .orElse(null);
            if (duplicate != null) {
                return duplicate;
            }
        }

        ProcessAiAnalysis analysis = ProcessAiAnalysis.builder()
                .processId(request.getProcessId())
                .processKey(request.getProcessKey())
                .processVersion(request.getProcessVersion())
                .processName(firstNonBlank(request.getProcessName(), response.getProcessName(), "Proceso sin nombre"))
                .score(response.getScore())
                .summary(response.getSummary())
                .issues(toIssues(response.getIssues()))
                .suggestions(toSuggestions(response.getSuggestions()))
                .status(ProcessAiAnalysisStatus.NEW)
                .fingerprint(fingerprint)
                .createdAt(LocalDateTime.now())
                .build();

        return analysisRepository.save(analysis);
    }

    private boolean shouldAnalyze(Proceso proceso) {
        if (proceso == null || proceso.getXml() == null || proceso.getXml().isBlank()) {
            return false;
        }

        ProcessAiAnalysis last = analysisRepository.findTopByProcessIdOrderByCreatedAtDesc(proceso.getId())
                .orElse(null);
        if (last == null) {
            return true;
        }

        LocalDateTime lastProcessChange = proceso.getLastSavedAt() != null
                ? proceso.getLastSavedAt()
                : proceso.getUpdatedAt();

        if (lastProcessChange != null && lastProcessChange.isAfter(last.getCreatedAt())) {
            return true;
        }

        return last.getCreatedAt().isBefore(LocalDateTime.now().minusHours(RECENT_DUPLICATE_HOURS));
    }

    private LocalDateTime lastActivityAt(Proceso proceso) {
        if (proceso == null) {
            return null;
        }
        if (proceso.getLastSavedAt() != null) {
            return proceso.getLastSavedAt();
        }
        return proceso.getUpdatedAt();
    }

    private boolean shouldNotify(ProcessAiAnalysis analysis) {
        if (analysis == null) {
            return false;
        }

        if (analysis.getScore() != null && analysis.getScore() < minScoreNotify) {
            return true;
        }

        String severity = notifySeverity == null ? "high" : notifySeverity.trim().toLowerCase();
        return analysis.getIssues() != null && analysis.getIssues().stream()
                .anyMatch(issue -> issue.getSeverity() != null && issue.getSeverity().trim().equalsIgnoreCase(severity));
    }

    private String buildNotificationMessage(ProcessAiAnalysis analysis) {
        String processName = firstNonBlank(analysis.getProcessName(), analysis.getProcessKey(), "Proceso sin nombre");
        String issue = analysis.getIssues() == null || analysis.getIssues().isEmpty()
                ? "una oportunidad de mejora"
                : analysis.getIssues().get(0).getDescription();
        return "IA detecto posible riesgo en el proceso \"" + processName + "\": " + issue;
    }

    private ProcessAnalysisResponseDto toDto(ProcessAiAnalysis analysis) {
        return ProcessAnalysisResponseDto.builder()
                .id(analysis.getId())
                .processId(analysis.getProcessId())
                .processKey(analysis.getProcessKey())
                .processVersion(analysis.getProcessVersion())
                .processName(analysis.getProcessName())
                .summary(analysis.getSummary())
                .score(analysis.getScore())
                .issues(toIssueDtos(analysis.getIssues()))
                .suggestions(toSuggestionDtos(analysis.getSuggestions()))
                .status(analysis.getStatus() == null ? null : analysis.getStatus().name())
                .createdAt(analysis.getCreatedAt())
                .build();
    }

    private List<ProcessAiAnalysisIssue> toIssues(List<ProcessAnalysisIssueDto> issues) {
        if (issues == null) {
            return List.of();
        }

        return issues.stream()
                .map(issue -> ProcessAiAnalysisIssue.builder()
                        .type(issue.getType())
                        .description(issue.getDescription())
                        .elementId(issue.getElementId())
                        .severity(issue.getSeverity())
                        .build())
                .toList();
    }

    private List<ProcessAiAnalysisSuggestion> toSuggestions(List<ProcessAnalysisSuggestionDto> suggestions) {
        if (suggestions == null) {
            return List.of();
        }

        return suggestions.stream()
                .map(suggestion -> ProcessAiAnalysisSuggestion.builder()
                        .title(suggestion.getTitle())
                        .description(suggestion.getDescription())
                        .impact(suggestion.getImpact())
                        .relatedElementId(suggestion.getRelatedElementId())
                        .canBeAppliedAutomatically(Boolean.TRUE.equals(suggestion.getCanBeAppliedAutomatically()))
                        .build())
                .toList();
    }

    private List<ProcessAnalysisIssueDto> toIssueDtos(List<ProcessAiAnalysisIssue> issues) {
        if (issues == null) {
            return List.of();
        }

        return issues.stream()
                .map(issue -> ProcessAnalysisIssueDto.builder()
                        .type(issue.getType())
                        .description(issue.getDescription())
                        .elementId(issue.getElementId())
                        .severity(issue.getSeverity())
                        .build())
                .toList();
    }

    private List<ProcessAnalysisSuggestionDto> toSuggestionDtos(List<ProcessAiAnalysisSuggestion> suggestions) {
        if (suggestions == null) {
            return List.of();
        }

        return suggestions.stream()
                .map(suggestion -> ProcessAnalysisSuggestionDto.builder()
                        .title(suggestion.getTitle())
                        .description(suggestion.getDescription())
                        .impact(suggestion.getImpact())
                        .relatedElementId(suggestion.getRelatedElementId())
                        .canBeAppliedAutomatically(Boolean.TRUE.equals(suggestion.getCanBeAppliedAutomatically()))
                        .build())
                .toList();
    }

    private String fingerprint(ProcessAnalysisResponseDto response) {
        String source = String.valueOf(response.getScore())
                + "|" + String.valueOf(response.getSummary())
                + "|" + String.valueOf(response.getIssues())
                + "|" + String.valueOf(response.getSuggestions());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(source.hashCode());
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
