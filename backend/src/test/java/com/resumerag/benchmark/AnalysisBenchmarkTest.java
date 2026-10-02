package com.resumerag.benchmark;

import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.AnalysisEngineFixture;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoreCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evaluation benchmark.
 *
 * <p>Runs every scenario through the real engine and checks the result against
 * hand-written expectations, then writes a report to {@code target/} so the same
 * numbers can be compared between runs. Repeated execution is the entire point:
 * a scoring change is only safe if the benchmark moves only where it was intended
 * to, and that cannot be seen from a pass/fail bit.
 *
 * <p>Semantic matching is stubbed, so the run is deterministic and does not
 * silently depend on whether a local LLM happens to be loaded. The semantic path
 * is covered separately by its own tests; mixing it in here would make every
 * calibration decision depend on an embedding model's opinion.
 *
 * <p>Regenerate the reports with:
 * <pre>mvn test -Dtest=AnalysisBenchmarkTest
 * mvn test -Dtest=AnalysisBenchmarkTest -Dbenchmark.write=true</pre>
 */
class AnalysisBenchmarkTest {

    private static final Path REPORT_DIR = Path.of("target");
    private static final AnalysisEngine ENGINE = AnalysisEngineFixture.engine();

    /**
     * The run is only interesting with the report written, so it is on by default
     * and can be switched off for a quick check.
     */
    private static final boolean WRITE_REPORT =
            Boolean.parseBoolean(System.getProperty("benchmark.write", "true"));

    @Test
    @DisplayName("Benchmark: every scenario behaves as its expectations describe")
    @EnabledIf("com.resumerag.benchmark.AnalysisBenchmarkTest#benchmarkEnabled")
    void benchmarkScenariosBehaveAsExpected() {
        List<BenchmarkCorpus.Scenario> scenarios = BenchmarkCorpus.load();
        assertTrue(scenarios.size() >= 20,
                "the benchmark needs at least 20 scenarios to be meaningful, found " + scenarios.size());

        List<BenchmarkObservation> observations = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (BenchmarkCorpus.Scenario scenario : scenarios) {
            BenchmarkObservation observation = run(scenario);
            observations.add(observation);
            String failure = verify(scenario, observation);
            if (failure != null) {
                failures.add(scenario.id() + ": " + failure);
            }
        }

        writeReports(observations, failures);

        assertTrue(failures.isEmpty(),
                failures.size() + " benchmark scenario(s) did not behave as expected:\n  "
                        + String.join("\n  ", failures)
                        + "\n\nSee target/benchmark-report.md for the full measurement table.");
    }

    /**
     * Allows the benchmark to be skipped in a normal build without losing it.
     * Set {@code -Dbenchmark.enabled=false} to run the rest of the suite only.
     */
    static boolean benchmarkEnabled() {
        return Boolean.parseBoolean(System.getProperty("benchmark.enabled", "true"));
    }

    private BenchmarkObservation run(BenchmarkCorpus.Scenario scenario) {
        try {
            AnalysisEngine.Result result = ENGINE.analyze(scenario.resume(), scenario.jobDescription(), null);
            return BenchmarkObservation.of(scenario, result);
        } catch (RuntimeException e) {
            throw new AssertionError("Scenario " + scenario.id() + " threw: " + e, e);
        }
    }

    /**
     * Checks one scenario.
     *
     * @return null when it behaved as expected, otherwise a description of what
     *         disagreed with the expectation
     */
    private String verify(BenchmarkCorpus.Scenario scenario, BenchmarkObservation o) {
        BenchmarkCorpus.Expectations e = scenario.expectations();

        if (e.overallRange() != null) {
            int actual = o.overallScore();
            if (actual < e.overallRange()[0] || actual > e.overallRange()[1]) {
                return "overall " + actual + " outside expected range ["
                        + e.overallRange()[0] + ", " + e.overallRange()[1] + "]";
            }
        }
        if (e.requiredSkillsRange() != null) {
            Double actual = o.scoreOf(ScoreCategory.REQUIRED_SKILLS);
            if (actual == null) {
                return "required skills were not assessed";
            }
            if (actual < e.requiredSkillsRange()[0] || actual > e.requiredSkillsRange()[1]) {
                return "required skills " + actual + " outside expected range ["
                        + e.requiredSkillsRange()[0] + ", " + e.requiredSkillsRange()[1] + "]";
            }
        }
        if (e.experienceRange() != null) {
            Double actual = o.scoreOf(ScoreCategory.EXPERIENCE);
            if (actual == null) {
                return "experience was not assessed";
            }
            if (actual < e.experienceRange()[0] || actual > e.experienceRange()[1]) {
                return "experience " + actual + " outside expected range ["
                        + e.experienceRange()[0] + ", " + e.experienceRange()[1] + "]";
            }
        }
        if (e.expectedExperienceStatus() != null) {
            String actual = o.experience() == null ? null
                    : (o.experience().status() == null ? null : o.experience().status().name());
            if (!e.expectedExperienceStatus().equals(actual)) {
                return "experience status " + actual + ", expected " + e.expectedExperienceStatus();
            }
        }
        if (e.minRequiredMatchedRatio() != null
                && o.requiredMatchedRatio() < e.minRequiredMatchedRatio()) {
            return "required matched ratio " + round(o.requiredMatchedRatio())
                    + " below expected minimum " + e.minRequiredMatchedRatio();
        }
        if (e.minResponsibilitiesMatchedRatio() != null
                && o.responsibilitiesMatchedRatio() < e.minResponsibilitiesMatchedRatio()) {
            return "responsibilities matched ratio " + round(o.responsibilitiesMatchedRatio())
                    + " below expected minimum " + e.minResponsibilitiesMatchedRatio();
        }
        if (e.minPreferredMatchedRatio() != null
                && o.preferredMatchedRatio() < e.minPreferredMatchedRatio()) {
            return "preferred matched ratio " + round(o.preferredMatchedRatio())
                    + " below expected minimum " + e.minPreferredMatchedRatio();
        }

        for (Map.Entry<String, String> expected : e.expectStatus().entrySet()) {
            RequirementMatch match = o.findRequirement(expected.getKey());
            if (match == null) {
                return "no requirement named \"" + expected.getKey() + "\" was extracted; found "
                        + requirementNames(o);
            }
            if (!match.status().name().equals(expected.getValue())) {
                return "\"" + expected.getKey() + "\" was " + match.status()
                        + ", expected " + expected.getValue();
            }
        }
        for (String name : e.expectNotExplicit()) {
            RequirementMatch match = o.findRequirement(name);
            if (match == null) {
                return "no requirement named \"" + name + "\" was extracted; found " + requirementNames(o);
            }
            if (match.status() != MatchStatus.NOT_EXPLICITLY_MENTIONED) {
                return "\"" + name + "\" was " + match.status() + ", expected NOT_EXPLICITLY_MENTIONED";
            }
        }
        for (String name : e.expectPartial()) {
            RequirementMatch match = o.findRequirement(name);
            if (match == null) {
                return "no requirement named \"" + name + "\" was extracted; found " + requirementNames(o);
            }
            if (match.status() != MatchStatus.PARTIAL_MATCH) {
                return "\"" + name + "\" was " + match.status() + ", expected PARTIAL_MATCH";
            }
        }
        for (String term : e.forbidSatisfiedTerms()) {
            List<RequirementMatch> offenders = o.scored().matches().stream()
                    .filter(m -> !m.isUnsatisfied())
                    .filter(m -> m.normalizedRequirement().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))
                    .toList();
            if (!offenders.isEmpty()) {
                return "no requirement should be satisfied by \"" + term + "\", but these were matched: "
                        + offenders.stream().map(RequirementMatch::requirement).toList();
            }
        }
        return null;
    }

    private static String requirementNames(BenchmarkObservation o) {
        return o.scored().matches().stream().map(RequirementMatch::requirement).toList().toString();
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ---------------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------------

    private void writeReports(List<BenchmarkObservation> observations, List<String> failures) {
        if (!WRITE_REPORT) {
            return;
        }
        try {
            Files.createDirectories(REPORT_DIR);
            Files.writeString(REPORT_DIR.resolve("benchmark-report.md"), markdown(observations, failures));
            Files.writeString(REPORT_DIR.resolve("benchmark-results.tsv"), tsv(observations));
            Files.writeString(REPORT_DIR.resolve("benchmark-requirements.tsv"), requirementsTsv(observations));
        } catch (IOException e) {
            System.err.println("Could not write the benchmark report: " + e.getMessage());
        }
    }

    /**
     * Every requirement, its state and its evidence, for every scenario.
     *
     * <p>The measurement table says a number moved; this says which requirement
     * moved it. Without it, diagnosing a scoring change means re-running the
     * engine by hand for each scenario, and the reason a change was made tends to
     * get lost by the time it is reviewed.
     */
    private String requirementsTsv(List<BenchmarkObservation> observations) {
        StringBuilder sb = new StringBuilder(
                "scenario\trequirement\tcategory\timportance\tstatus\tconfidence\tevidenceStrength\t"
                        + "provenance\tevidence\n");
        for (BenchmarkObservation o : observations) {
            for (RequirementMatch m : o.scored().matches()) {
                sb.append(o.scenario().id()).append('\t')
                  .append(m.requirement()).append('\t')
                  .append(m.category()).append('\t')
                  .append(m.importance()).append('\t')
                  .append(m.status()).append('\t')
                  .append(m.confidence()).append('\t')
                  .append(m.evidenceStrength()).append('\t')
                  .append(m.provenance()).append('\t')
                  .append(String.join(" | ", m.resumeEvidence())).append('\n');
            }
        }
        return sb.toString();
    }

    private String markdown(List<BenchmarkObservation> observations, List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HireLens analysis benchmark\n\n");
        sb.append("Generated by `AnalysisBenchmarkTest`. Every scenario is run through the real ")
          .append("`AnalysisEngine` with semantic matching stubbed, so the run is deterministic.\n\n");
        sb.append("Expected values are hand-written ranges and states, never exact scores. ")
          .append("A benchmark that pins totals cannot survive a legitimate change to the scoring ")
          .append("constants, and pinning them is how a benchmark gets bent to fit whatever the ")
          .append("engine happens to produce.\n\n");

        sb.append("## Results\n\n");
        sb.append("| Scenario | Group | Expected | Actual | Req | Exp | Resp | Pref | Matched | Gaps | Result |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (BenchmarkObservation o : observations) {
            BenchmarkCorpus.Expectations e = o.scenario().expectations();
            sb.append("| ").append(o.scenario().id())
              .append(" | ").append(o.scenario().group())
              .append(" | ").append(e.overallRange() == null ? "-" : range(e.overallRange()))
              .append(" | **").append(o.overallScore()).append("**")
              .append(" | ").append(num(o.scoreOf(ScoreCategory.REQUIRED_SKILLS)))
              .append(" | ").append(num(o.scoreOf(ScoreCategory.EXPERIENCE)))
              .append(" | ").append(num(o.scoreOf(ScoreCategory.RESPONSIBILITIES)))
              .append(" | ").append(num(o.scoreOf(ScoreCategory.PREFERRED_SKILLS)))
              .append(" | ").append(o.matched() + o.contextual()).append("/").append(o.requirements())
              .append(" | ").append(o.notExplicit())
              .append(" | ").append(failures.stream().anyMatch(f -> f.startsWith(o.scenario().id() + ":"))
                        ? "**FAIL**" : "PASS")
              .append(" |\n");
        }

        sb.append("\n## Match states\n\n");
        sb.append("| Scenario | Explicit | Contextual | Partial | Not explicit | With evidence | Critical missing |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (BenchmarkObservation o : observations) {
            sb.append("| ").append(o.scenario().id())
              .append(" | ").append(o.matched())
              .append(" | ").append(o.contextual())
              .append(" | ").append(o.partial())
              .append(" | ").append(o.notExplicit())
              .append(" | ").append(o.withEvidence()).append("/").append(o.requirements())
              .append(" | ").append(o.criticalMissing()).append("/").append(o.criticalTotal())
              .append(" |\n");
        }

        sb.append("\n## Experience alignment detail\n\n");
        sb.append("Deliberately verbose: an experience dimension reading `null` when a JD states a band ")
          .append("is a finding in itself, and a band reading 3.6 with no visible candidate figure is a bug ")
          .append("report, not a scoring choice.\n\n");
        sb.append("| Scenario | JD min | JD max | Candidate years | Status | Score |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (BenchmarkObservation o : observations) {
            var e = o.experience();
            sb.append("| ").append(o.scenario().id())
              .append(" | ").append(e == null ? "-" : e.requiredMinYears())
              .append(" | ").append(e == null ? "-" : e.requiredMaxYears())
              .append(" | ").append(e == null || e.candidateYears() == null ? "**none**" : e.candidateYears())
              .append(" | ").append(e == null || e.status() == null ? "-" : e.status())
              .append(" | ").append(o.scoreOf(ScoreCategory.EXPERIENCE))
              .append(" |\n");
        }

        sb.append("\n## Unmet requirements per scenario\n\n");
        sb.append("The evidence for what a score is actually made of. Long lists here are legitimate ")
          .append("(a genuinely mismatched pair) but so is a short list with a high score.\n\n");
        for (BenchmarkObservation o : observations) {
            List<String> unmet = o.unmetRequirements();
            sb.append("- **").append(o.scenario().id()).append("** (").append(o.overallScore())
              .append("%): ");
            sb.append(unmet.isEmpty() ? "_none_" : String.join("; ", unmet)).append('\n');
        }

        sb.append("\n## Scenario rationale\n\n");
        for (BenchmarkObservation o : observations) {
            String rationale = o.scenario().expectations().rationale();
            if (rationale == null) {
                continue;
            }
            sb.append("- **").append(o.scenario().id()).append("** - ").append(rationale).append('\n');
        }

        if (!failures.isEmpty()) {
            sb.append("\n## Failures\n\n");
            for (String failure : failures) {
                sb.append("- ").append(failure).append('\n');
            }
        }
        return sb.toString();
    }

    private String tsv(List<BenchmarkObservation> observations) {
        StringBuilder sb = new StringBuilder(
                "scenario\tgroup\toverall\tlabel\trequired\texperience\tresponsibilities\tpreferred\t"
                        + "projects\teducation\tats\trequirements\tmatched\tpartial\tnotExplicit\tcriticalMissing\n");
        for (BenchmarkObservation o : observations) {
            sb.append(o.scenario().id()).append('\t')
              .append(o.scenario().group()).append('\t')
              .append(o.overallScore()).append('\t')
              .append(o.matchLabel()).append('\t')
              .append(num(o.scoreOf(ScoreCategory.REQUIRED_SKILLS))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.EXPERIENCE))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.RESPONSIBILITIES))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.PREFERRED_SKILLS))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.PROJECTS))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.EDUCATION))).append('\t')
              .append(num(o.scoreOf(ScoreCategory.ATS))).append('\t')
              .append(o.requirements()).append('\t')
              .append(o.matched()).append('\t')
              .append(o.partial()).append('\t')
              .append(o.notExplicit()).append('\t')
              .append(o.criticalMissing()).append('\n');
        }
        return sb.toString();
    }

    private static String range(Integer[] range) {
        return range[0] + "-" + range[1];
    }

    private static String num(Double value) {
        return value == null ? "null" : (value == Math.rint(value)
                ? String.valueOf((long) value.doubleValue())
                : String.valueOf(value));
    }
}
