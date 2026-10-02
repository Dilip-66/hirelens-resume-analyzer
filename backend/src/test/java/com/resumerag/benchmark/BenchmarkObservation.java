package com.resumerag.benchmark;

import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.model.CategoryScore;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.analysis.model.ScoredAnalysis;

import java.util.ArrayList;
import java.util.List;

/**
 * The measurements taken from one benchmark scenario.
 *
 * <p>Deliberately verbose. The point of a calibration pass is to look at the
 * component numbers next to each other and ask why one of them looks wrong - which
 * is impossible from a single percentage, and is exactly the mistake that got the
 * original flat keyword score into production.
 */
public record BenchmarkObservation(
        BenchmarkCorpus.Scenario scenario,
        ScoredAnalysis scored,
        int requirements,
        int matched,
        int contextual,
        int partial,
        int notExplicit,
        int withEvidence,
        int criticalMissing,
        int criticalTotal,
        String failure
) {

    public static BenchmarkObservation of(BenchmarkCorpus.Scenario scenario, AnalysisEngine.Result result) {
        ScoredAnalysis scored = result.scored();
        List<RequirementMatch> matches = scored.matches();

        int matched = count(matches, MatchStatus.EXPLICIT_MATCH);
        int contextual = count(matches, MatchStatus.STRONG_CONTEXTUAL_MATCH);
        int partial = count(matches, MatchStatus.PARTIAL_MATCH);
        int notExplicit = count(matches, MatchStatus.NOT_EXPLICITLY_MENTIONED)
                + count(matches, MatchStatus.CONFLICT);
        int withEvidence = (int) matches.stream().filter(m -> !m.resumeEvidence().isEmpty()).count();

        List<RequirementMatch> critical = matches.stream()
                .filter(m -> m.importance() == com.resumerag.analysis.model.RequirementImportance.CRITICAL)
                .toList();
        int criticalMissing = (int) critical.stream().filter(RequirementMatch::isUnsatisfied).count();

        return new BenchmarkObservation(scenario, scored, matches.size(), matched, contextual, partial,
                notExplicit, withEvidence, criticalMissing, critical.size(), null);
    }

    public BenchmarkObservation failed(String reason) {
        return new BenchmarkObservation(scenario, scored, requirements, matched, contextual, partial,
                notExplicit, withEvidence, criticalMissing, criticalTotal, reason);
    }

    private static int count(List<RequirementMatch> matches, MatchStatus status) {
        return (int) matches.stream().filter(m -> m.status() == status).count();
    }

    public int overallScore() {
        return scored.overallScore();
    }

    public String matchLabel() {
        return scored.matchLabel().displayName();
    }

    public Double scoreOf(ScoreCategory category) {
        CategoryScore score = scored.category(category);
        return score == null ? null : score.score();
    }

    public double requiredMatchedRatio() {
        return ratio(RequirementCategory.REQUIRED_SKILL, m -> !m.isUnsatisfied());
    }

    public double preferredMatchedRatio() {
        return ratio(RequirementCategory.PREFERRED_SKILL, m -> !m.isUnsatisfied());
    }

    public double responsibilitiesMatchedRatio() {
        return ratio(RequirementCategory.RESPONSIBILITY, m -> !m.isUnsatisfied());
    }

    private double ratio(RequirementCategory category, java.util.function.Predicate<RequirementMatch> test) {
        List<RequirementMatch> inCategory = scored.matchesIn(category);
        if (inCategory.isEmpty()) {
            return 0.0;
        }
        return (double) inCategory.stream().filter(test).count() / inCategory.size();
    }

    public ExperienceAlignment experience() {
        return scored.experienceAlignment();
    }

    public RequirementMatch findRequirement(String name) {
        return scored.matches().stream()
                .filter(m -> m.requirement().equals(name))
                .findFirst()
                .orElse(null);
    }

    /** Requirement names the engine considered unsatisfied, most important first. */
    public List<String> unmetRequirements() {
        List<String> names = new ArrayList<>();
        for (RequirementMatch match : scored.matches()) {
            if (match.isUnsatisfied()) {
                names.add(match.requirement());
            }
        }
        return names;
    }
}
