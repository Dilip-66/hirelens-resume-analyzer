package com.resumerag.generation;

import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Checks the model's narrative against what the engine actually found.
 *
 * <p>The model writes prose; it does not decide anything. That separation only
 * holds if the prose is checked, because a model asked to "list strengths and
 * gaps" will cheerfully restate a job description line as a strength - quoting
 * "Developed REST APIs using Java and Spring Boot" from the job description as
 * something the candidate did, and listing "Participate in code reviews" as a gap
 * without ever saying the resume is silent about it.
 *
 * <p>Observed on the real validation case with a 7B model, given a prompt that
 * already forbade exactly that. Prompting harder is not the fix: the safe move is
 * to make the claim impossible to assert. Anything the model returns that does not
 * correspond to a requirement the engine matched is dropped, and any gap is
 * rewritten into the wording the evidence actually supports.
 *
 * <p>When validation leaves nothing, the deterministic lists are used instead. A
 * plainer report is strictly better than one that overclaims.
 */
@Component
public class NarrativeValidator {

    private static final Logger log = LoggerFactory.getLogger(NarrativeValidator.class);

    /**
     * Reconciles a model-written narrative with the engine's findings.
     *
     * @param narrative    what the model produced; the summary is kept as written
     *                     because prose cannot claim a skill the requirements list
     *                     does not contain
     * @param matches      the classified requirements, the source of truth
     * @param fallback     the deterministic strengths and gaps
     */
    public AnalysisNarrative validate(AnalysisNarrative narrative,
                                      List<RequirementMatch> matches,
                                      List<String> fallbackStrengths,
                                      List<String> fallbackGaps) {
        if (narrative == null) {
            return new AnalysisNarrative("", fallbackStrengths, fallbackGaps);
        }

        List<String> strengths = keepOnly(narrative.strengths(), matches, true, fallbackStrengths);
        List<String> gaps = rewriteGaps(narrative.gaps(), matches, fallbackGaps);

        if (log.isDebugEnabled() && (!strengths.equals(narrative.strengths()) || !gaps.equals(narrative.gaps()))) {
            log.debug("Narrative reconciled against the match results: {} strength(s) and {} gap(s) "
                            + "adjusted", narrative.strengths().size() - strengths.size(),
                    narrative.gaps().size() - gaps.size());
        }
        return new AnalysisNarrative(narrative.summary(), strengths, gaps);
    }

    /**
     * Keeps a model-written strength only if it names something the engine matched.
     *
     * <p>Matched loosely, on the requirement name appearing in the sentence. A
     * model that says "strong Java background" for a matched Java requirement is
     * saying something true; a model that invents "Kubernetes experience" is not,
     * and cannot be caught by any amount of prompting.
     */
    private List<String> keepOnly(List<String> written, List<RequirementMatch> matches,
                                  boolean requireMatched, List<String> fallback) {
        if (written == null || written.isEmpty()) {
            return fallback;
        }
        List<String> kept = written.stream()
                .filter(line -> line != null && !line.isBlank())
                .filter(line -> matches.stream().anyMatch(match -> describes(match, line, requireMatched)))
                .toList();
        return kept.isEmpty() ? fallback : capped(kept, fallback.size() + 3);
    }

    /**
     * Rewrites gaps into the wording the evidence supports.
     *
     * <p>Not filtered, because a model that reaches a true conclusion in the wrong
     * words is still worth keeping - but the wording is ours, because "Code
     * reviews" listed as a gap tells a candidate they were found wanting, and the
     * evidence only supports "the resume does not mention it".
     */
    private List<String> rewriteGaps(List<String> written, List<RequirementMatch> matches,
                                     List<String> fallback) {
        if (written == null || written.isEmpty()) {
            return fallback;
        }
        List<String> rewritten = new java.util.ArrayList<>();
        for (String line : written) {
            if (line == null || line.isBlank()) {
                continue;
            }
            matches.stream()
                    .filter(match -> match.isUnsatisfied())
                    .filter(match -> describes(match, line, false))
                    .findFirst()
                    .ifPresentOrElse(
                            match -> rewritten.add(match.requirement()
                                    + " - not explicitly mentioned in the resume"),
                            () -> {
                                // The model raised something the engine has no
                                // finding for. Dropped rather than passed on: an
                                // unexplained gap is a claim with no evidence
                                // behind it, which is the thing this whole report
                                // format exists to avoid.
                            });
        }
        return rewritten.isEmpty() ? fallback : capped(rewritten, fallback.size() + 3);
    }

    /**
     * Whether a sentence is talking about this requirement, and whether that is
     * consistent with its match state.
     */
    private boolean describes(RequirementMatch match, String sentence, boolean requireMatched) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        boolean named = containsName(lower, match.requirement())
                || match.normalizedRequirement().toLowerCase(Locale.ROOT).length() > 3
                   && lower.contains(match.normalizedRequirement().toLowerCase(Locale.ROOT));
        if (!named) {
            return false;
        }
        boolean matched = match.status() == MatchStatus.EXPLICIT_MATCH
                || match.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH;
        return requireMatched ? matched : !matched;
    }

    /**
     * Name containment on token boundaries.
     *
     * <p>Plain {@code contains} would match a requirement called "Go" inside
     * "going to mention", which is the same class of mistake the whole evidence
     * layer is built to avoid.
     */
    private boolean containsName(String lowerSentence, String requirement) {
        String lowerName = requirement.toLowerCase(Locale.ROOT);
        if (lowerName.isBlank()) {
            return false;
        }
        int from = 0;
        while (true) {
            int index = lowerSentence.indexOf(lowerName, from);
            if (index < 0) {
                return false;
            }
            int end = index + lowerName.length();
            boolean startOk = index == 0 || !Character.isLetterOrDigit(lowerSentence.charAt(index - 1));
            boolean endOk = end == lowerSentence.length() || !Character.isLetterOrDigit(lowerSentence.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = index + 1;
        }
    }

    /** Keeps the report's list lengths in the same range the engine produced. */
    private List<String> capped(List<String> values, int limit) {
        return values.size() <= limit ? values : values.subList(0, limit);
    }
}
