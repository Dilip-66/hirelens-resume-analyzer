package com.resumerag.pipeline.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.model.AnalysisReportMeta;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoredAnalysis;
import com.resumerag.model.Analysis;
import com.resumerag.model.AnalysisMatch;
import com.resumerag.model.AnalysisRequirement;
import com.resumerag.model.AnalysisScoreBreakdown;
import com.resumerag.repository.AnalysisMatchRepository;
import com.resumerag.repository.AnalysisRepository;
import com.resumerag.repository.AnalysisRequirementRepository;
import com.resumerag.repository.AnalysisScoreBreakdownRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes an analysis and the evidence chain behind it.
 *
 * <p>All of it in one transaction, and only after the analysis row exists - the
 * detail rows carry a foreign key to it, so a partial write would leave orphaned
 * requirements behind a report that claims to have none.
 *
 * <p>{@link Propagation#REQUIRES_NEW} because the embedding and model calls that
 * produce this data deliberately run outside any transaction: a pooled connection
 * held across a 300-second LLM call is how a five-connection pool starves every
 * other request. Opening the write transaction only here, at the end, keeps that
 * property while still making the writes atomic.
 */
@Component
public class AnalysisDetailWriter {

    private final AnalysisRepository analysisRepository;
    private final AnalysisRequirementRepository requirementRepository;
    private final AnalysisMatchRepository matchRepository;
    private final AnalysisScoreBreakdownRepository breakdownRepository;
    private final ObjectMapper objectMapper;

    public AnalysisDetailWriter(AnalysisRepository analysisRepository,
                                AnalysisRequirementRepository requirementRepository,
                                AnalysisMatchRepository matchRepository,
                                AnalysisScoreBreakdownRepository breakdownRepository,
                                ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.requirementRepository = requirementRepository;
        this.matchRepository = matchRepository;
        this.breakdownRepository = breakdownRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists the reasoning behind a saved analysis and attaches the report
     * header to it.
     *
     * <p>The header is written in the same transaction as the detail rows. Written
     * separately it could land without them, which renders as a report claiming a
     * score with no requirements behind it - the one state a user cannot audit.
     *
     * @return the analysis with its header attached
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Analysis write(Analysis analysis, AnalysisEngine.Result result) {
        ScoredAnalysis scored = result.scored();

        Map<Integer, RequirementMatch> matchesByIndex = scored.matches().stream()
                .collect(Collectors.toMap(RequirementMatch::requirementIndex,
                        Function.identity(), (first, ignored) -> first));

        List<AnalysisRequirement> requirementRows = scored.requirements().stream()
                .map(requirement -> new AnalysisRequirement(
                        analysis.getId(), requirement.index(), requirement.name(),
                        requirement.normalizedName(), requirement.category().name(),
                        requirement.importance().name(), requirement.demand().name(),
                        requirement.sourceText(), writeJson(requirement.synonyms()),
                        requirement.explicitRequirement()))
                .toList();

        List<AnalysisRequirement> savedRequirements = requirementRepository.saveAll(requirementRows);

        List<AnalysisMatch> matchRows = new ArrayList<>();
        for (AnalysisRequirement saved : savedRequirements) {
            RequirementMatch match = matchesByIndex.get(saved.getRequirementIndex());
            if (match != null) {
                matchRows.add(new AnalysisMatch(
                        saved.getId(), match.status().name(), match.confidence(),
                        match.evidenceStrength(), match.provenance().name(),
                        writeJson(match.resumeEvidence()), writeJson(match.jdEvidence()),
                        match.explanation()));
            }
        }
        matchRepository.saveAll(matchRows);

        breakdownRepository.saveAll(scored.categoryScores().stream()
                .map(score -> new AnalysisScoreBreakdown(
                        analysis.getId(), score.category().name(), score.score(), score.weight(),
                        score.weightedScore(), score.requirementsConsidered(), score.note()))
                .toList());

        analysis.setDetailsJson(writeJson(new AnalysisReportMeta(
                scored.overallScore(),
                !scored.matches().isEmpty(),
                scored.matchLabel().displayName(),
                scored.assessedWeight(),
                scored.experienceAlignment(),
                result.recommendations())));

        return analysisRepository.save(analysis);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize analysis detail", e);
        }
    }
}
