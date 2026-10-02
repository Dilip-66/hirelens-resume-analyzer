package com.resumerag.pipeline.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.analysis.model.AnalysisReportMeta;
import com.resumerag.dto.analysis.AnalysisDetailResponse;
import com.resumerag.dto.analysis.CategoryScoreResponse;
import com.resumerag.dto.analysis.ExperienceAlignmentResponse;
import com.resumerag.dto.analysis.RequirementMatchResponse;
import com.resumerag.dto.analysis.ScoreBreakdownResponse;
import com.resumerag.model.Analysis;
import com.resumerag.model.AnalysisMatch;
import com.resumerag.model.AnalysisRequirement;
import com.resumerag.model.AnalysisScoreBreakdown;
import com.resumerag.repository.AnalysisMatchRepository;
import com.resumerag.repository.AnalysisRequirementRepository;
import com.resumerag.repository.AnalysisScoreBreakdownRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads a stored analysis back into the API shape.
 *
 * <p>Three queries per report, not per requirement: a report with thirty
 * requirements would otherwise run sixty-two queries to render one page, and the
 * history list multiplies that by every row on it.
 *
 * <p>Returns {@code null} - not an empty shell - for a report analysed before
 * evidence-based scoring existed. The client then renders it exactly as it always
 * did, which is the honest outcome: there is no stored reasoning to show, and a
 * response full of nulls would read as a failure rather than as an older report.
 */
@Service
public class AnalysisDetailReader {

    private static final Logger log = LoggerFactory.getLogger(AnalysisDetailReader.class);

    private final AnalysisRequirementRepository requirementRepository;
    private final AnalysisMatchRepository matchRepository;
    private final AnalysisScoreBreakdownRepository breakdownRepository;
    private final ObjectMapper objectMapper;

    public AnalysisDetailReader(AnalysisRequirementRepository requirementRepository,
                                AnalysisMatchRepository matchRepository,
                                AnalysisScoreBreakdownRepository breakdownRepository,
                                ObjectMapper objectMapper) {
        this.requirementRepository = requirementRepository;
        this.matchRepository = matchRepository;
        this.breakdownRepository = breakdownRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * The detail for one analysis, or {@code null} when it has none.
     *
     * <p>Read-only and outside the caller's transaction. This runs long after the
     * analysis was written, and holding a pooled connection across that gap is how
     * a small pool gets starved by report views.
     */
    @Transactional(readOnly = true)
    public AnalysisDetailResponse read(Analysis analysis) {
        if (analysis == null || analysis.getDetailsJson() == null || analysis.getDetailsJson().isBlank()) {
            return null;
        }
        AnalysisReportMeta meta = readMeta(analysis.getDetailsJson());
        if (meta == null) {
            return null;
        }

        List<AnalysisRequirement> requirements =
                requirementRepository.findByAnalysisIdOrderByRequirementIndexAsc(analysis.getId());
        if (requirements.isEmpty()) {
            // Header without requirements. Possible only for a partially written
            // analysis, so report the header and nothing more rather than
            // pretending there were requirements.
            return new AnalysisDetailResponse(meta.scored(), meta.matchLabel(),
                    (double) meta.overallScore(), new ScoreBreakdownResponse(null, null, null, null, null, null, null, List.of()),
                    ExperienceAlignmentResponse.from(meta.experienceAlignment()), List.of(),
                    meta.recommendations(), 0, 0);
        }

        Map<UUID, AnalysisMatch> matchesByRequirement = matchRepository
                .findByAnalysisRequirementIdInOrderByIdAsc(requirements.stream()
                        .map(AnalysisRequirement::getId).toList())
                .stream()
                .collect(Collectors.toMap(AnalysisMatch::getAnalysisRequirementId,
                        Function.identity(), (first, ignored) -> first, LinkedHashMap::new));

        List<RequirementMatchResponse> responses = new ArrayList<>(requirements.size());
        for (AnalysisRequirement requirement : requirements) {
            responses.add(toResponse(requirement, matchesByRequirement.get(requirement.getId())));
        }

        List<CategoryScoreResponse> categories = toCategoryScores(
                breakdownRepository.findByAnalysisIdOrderByIdAsc(analysis.getId()));

        return new AnalysisDetailResponse(
                meta.scored(),
                meta.matchLabel(),
                (double) meta.overallScore(),
                new ScoreBreakdownResponse(
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.REQUIRED_SKILLS),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.EXPERIENCE),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.RESPONSIBILITIES),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.PREFERRED_SKILLS),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.PROJECTS),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.EDUCATION),
                        scoreOf(categories, com.resumerag.analysis.model.ScoreCategory.ATS),
                        categories),
                ExperienceAlignmentResponse.from(meta.experienceAlignment()),
                responses,
                meta.recommendations(),
                responses.size(),
                (int) responses.stream().filter(r -> !r.resumeEvidence().isEmpty()).count());
    }

    private RequirementMatchResponse toResponse(AnalysisRequirement requirement, AnalysisMatch match) {
        if (match == null) {
            // A requirement with no stored match means the analysis was written
            // partway through. Report it as unevidenced rather than dropping it -
            // a requirement that silently disappears is worse than one shown as
            // having no evidence.
            return new RequirementMatchResponse(
                    requirement.getRequirementIndex(), requirement.getName(),
                    requirement.getNormalizedName(), category(requirement.getCategory()),
                    importance(requirement.getImportance()),
                    com.resumerag.analysis.model.MatchStatus.NOT_EXPLICITLY_MENTIONED.name(),
                    0.0, 0, com.resumerag.analysis.model.MatchProvenance.NOT_EXPLICIT.name(),
                    List.of(), List.of(requirement.getSourceText()),
                    "No match result was recorded for this requirement.");
        }
        return new RequirementMatchResponse(
                requirement.getRequirementIndex(), requirement.getName(),
                requirement.getNormalizedName(), category(requirement.getCategory()),
                importance(requirement.getImportance()),
                match.getStatus(), match.getConfidence(), match.getEvidenceStrength(),
                match.getProvenance(),
                readStrings(match.getResumeEvidenceJson()), readStrings(match.getJdEvidenceJson()),
                match.getExplanation());
    }

    /**
     * Rebuilds the dimension scores, including the ones that were never assessed.
     *
     * <p>Emitted for every category even when absent from storage, so the
     * breakdown always has the same shape and a client never has to distinguish
     * "not measured" from "missing from the response".
     */
    private List<CategoryScoreResponse> toCategoryScores(List<AnalysisScoreBreakdown> breakdowns) {
        Map<com.resumerag.analysis.model.ScoreCategory, AnalysisScoreBreakdown> byCategory =
                new EnumMap<>(com.resumerag.analysis.model.ScoreCategory.class);
        for (AnalysisScoreBreakdown breakdown : breakdowns) {
            byCategory.putIfAbsent(scoreCategory(breakdown.getCategory()), breakdown);
        }
        List<CategoryScoreResponse> scores = new ArrayList<>();
        for (com.resumerag.analysis.model.ScoreCategory category
                : com.resumerag.analysis.model.ScoreCategory.values()) {
            AnalysisScoreBreakdown breakdown = byCategory.get(category);
            if (breakdown == null) {
                scores.add(CategoryScoreResponse.from(
                        com.resumerag.analysis.model.CategoryScore.notAssessed(
                                category, category.defaultWeight(), null)));
            } else {
                scores.add(CategoryScoreResponse.from(
                        new com.resumerag.analysis.model.CategoryScore(category, breakdown.getScore(),
                                breakdown.getWeight(), breakdown.getWeightedScore(),
                                breakdown.getRequirementsConsidered(), breakdown.getNote())));
            }
        }
        return scores;
    }

    /**
     * The stored score for one dimension, or {@code null} when it was not assessed.
     *
     * <p>Iterated rather than streamed, because the value is {@code null} for every
     * unassessed dimension and {@code findFirst()} throws on a null element - which
     * would make the "not measured" case the one that fails.
     */
    private Double scoreOf(List<CategoryScoreResponse> scores,
                           com.resumerag.analysis.model.ScoreCategory category) {
        for (CategoryScoreResponse score : scores) {
            if (score != null && score.category() == category) {
                return score.score();
            }
        }
        return null;
    }

    private AnalysisReportMeta readMeta(String json) {
        try {
            return objectMapper.readValue(json, AnalysisReportMeta.class);
        } catch (Exception e) {
            // A report whose header cannot be read is reported without its detail
            // rather than failing the request: the score and the skill lists on the
            // analysis row are still perfectly usable.
            log.warn("Stored analysis details could not be read; reporting the analysis without them: {}",
                    e.getMessage());
            return null;
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return values == null ? List.of() : values;
        } catch (Exception e) {
            log.warn("Stored evidence array could not be read; reporting it as empty: {}",
                    e.getMessage());
            return List.of();
        }
    }

    private com.resumerag.analysis.model.RequirementCategory category(String value) {
        try {
            return com.resumerag.analysis.model.RequirementCategory.valueOf(value);
        } catch (Exception e) {
            return com.resumerag.analysis.model.RequirementCategory.GENERAL;
        }
    }

    private com.resumerag.analysis.model.RequirementImportance importance(String value) {
        try {
            return com.resumerag.analysis.model.RequirementImportance.valueOf(value);
        } catch (Exception e) {
            return com.resumerag.analysis.model.RequirementImportance.LOW;
        }
    }

    private com.resumerag.analysis.model.ScoreCategory scoreCategory(String value) {
        try {
            return com.resumerag.analysis.model.ScoreCategory.valueOf(value);
        } catch (Exception e) {
            return com.resumerag.analysis.model.ScoreCategory.REQUIRED_SKILLS;
        }
    }
}
