package com.resumerag.analysis.scoring;

import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoredAnalysis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns classified requirements into strengths, gaps and actionable advice.
 *
 * <p>Deterministic and derived from the evidence, never from a model. The
 * narrative generator writes a summary in prose later; this decides what the
 * facts are, so a model cannot promote a "not explicitly mentioned" AI/ML
 * requirement into a reason not to hire someone.
 *
 * <p>Wording is the load-bearing part. Everything here describes the document, not
 * the person: a gap is a place where the resume is silent, and the advice says
 * what to add and where, without asserting the candidate cannot do the work.
 */
@Service
public class AnalysisRecommendationService {

    private static final int MAX_STRENGTHS = 5;
    private static final int MAX_GAPS = 5;
    private static final int MAX_RECOMMENDATIONS = 5;

    /**
     * The strongest evidenced requirements.
     *
     * <p>Ranked by importance and confidence, so "Java" appears above a
     * preferred-skill match even if the latter is the better-evidenced of the two -
     * which is what makes the list useful to a reader rather than merely complete.
     */
    public List<String> strengths(List<RequirementMatch> matches) {
        return matches.stream()
                .filter(m -> m.status() == MatchStatus.EXPLICIT_MATCH
                        || m.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH)
                .sorted(Comparator
                        .comparingInt((RequirementMatch m) -> m.importance().ordinal()).reversed()
                        .thenComparing(Comparator.comparingDouble(RequirementMatch::confidence).reversed()))
                .map(m -> m.requirement() + " (" + evidencePhrase(m) + ")")
                .limit(MAX_STRENGTHS)
                .toList();
    }

    /**
     * The requirements the resume is silent on.
     *
     * <p>Phrased as silence. "Code reviews - not explicitly mentioned in the
     * resume" is a statement a candidate can act on; "cannot do code reviews" is
     * a claim the evidence does not support and that they have no way to refute.
     */
    public List<String> gaps(List<RequirementMatch> matches) {
        return matches.stream()
                .filter(RequirementMatch::isUnsatisfied)
                .filter(m -> m.category() != RequirementCategory.EXPERIENCE)
                .sorted(Comparator
                        .comparingInt((RequirementMatch m) -> m.category().ordinal())
                        .thenComparingInt(m -> m.importance().ordinal()))
                .map(m -> m.requirement() + " - not explicitly mentioned in the resume")
                .limit(MAX_GAPS)
                .toList();
    }

    /**
     * What to do about the gaps, in the order worth doing them.
     *
     * <p>Priority follows importance, not the size of the deduction: adding
     * evidence for a critical required skill is worth far more than evidencing a
     * bonus, and telling a candidate otherwise wastes their time.
     */
    public List<String> recommendations(ScoredAnalysis scored) {
        List<String> recommendations = new ArrayList<>();

        for (RequirementMatch match : scored.matches()) {
            if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                break;
            }
            if (match.status() == MatchStatus.NOT_EXPLICITLY_MENTIONED) {
                if (match.importance() == RequirementImportance.LOW
                        && match.category() == RequirementCategory.PREFERRED_SKILL) {
                    continue; // A bonus is not worth a rewrite; mention it, do not prescribe.
                }
                recommendations.add("Add a concrete line for " + match.requirement()
                        + " - the job description asks for it and the resume does not mention it. Name it in "
                        + "the role where you used it and what you did with it.");
            } else if (match.status() == MatchStatus.PARTIAL_MATCH
                    && match.category() == RequirementCategory.REQUIRED_SKILL) {
                recommendations.add("Show more depth for " + match.requirement()
                        + ". It appears on your resume, but the role asks for more than a mention - a "
                        + "specific outcome would close the gap.");
            }
        }

        ExperienceAlignment experience = scored.experienceAlignment();
        if (experience != null
                && experience.status() == ExperienceAlignment.Status.BELOW_RANGE
                && recommendations.size() < MAX_RECOMMENDATIONS) {
            recommendations.add("This role asks for " + experience.requiredMinYears() + "+ years of "
                    + "experience and your resume states " + experience.candidateYears()
                    + ". Lead with the most relevant work and its scale rather than total years.");
        }
        return recommendations;
    }

    /** A short description of how a match was evidenced, for the strengths list. */
    private String evidencePhrase(RequirementMatch match) {
        return switch (match.status()) {
            case EXPLICIT_MATCH -> match.evidenceStrength() >= 4
                    ? "demonstrated in professional work"
                    : "listed as a skill";
            case STRONG_CONTEXTUAL_MATCH -> "supported by related evidence";
            default -> "partially evidenced";
        };
    }
}
