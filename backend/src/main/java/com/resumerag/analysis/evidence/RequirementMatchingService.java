package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.model.DemandLevel;
import com.resumerag.analysis.model.EvidenceCandidate;
import com.resumerag.analysis.model.EvidenceStrength;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.ExperienceRequirement;
import com.resumerag.analysis.model.MatchProvenance;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.NormalizedTerm;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns evidence into one of four match states, with a confidence and a reason.
 *
 * <p>The two-state model this replaces - matched or missing - is the reason a
 * keyword counter could not be made trustworthy. It has no way to say "this
 * person has used it, and here is where", so a name in a skills list and five
 * years of shipping it are the same observation, and a plausible-sounding guess
 * is the same as a quotation.
 *
 * <p>Classification is arithmetic over the evidence that was actually found:
 * strength of the best evidence, whether it meets the demand the job description
 * expressed, and whether a compound requirement was fully satisfied. No model is
 * consulted. That is what lets the same resume and job description produce the
 * same score on every run, and what makes the score arguable.
 */
@Service
public class RequirementMatchingService {

    private final ScoringConfiguration scoringConfiguration;

    public RequirementMatchingService(ScoringConfiguration scoringConfiguration) {
        this.scoringConfiguration = scoringConfiguration;
    }

    /**
     * Classifies one requirement.
     *
     * @param candidates every piece of evidence found, strongest first. The
     *                   classification uses all of it and only what is stored
     *                   for display is truncated - a term evidenced by the
     *                   fourth-best line still counts as evidenced, which it
     *                   would not if the report's snippet limit were applied
     *                   first.
     */
    public RequirementMatch match(Requirement requirement, List<EvidenceCandidate> candidates) {
        List<EvidenceCandidate> evidence = supporting(candidates);

        if (evidence.isEmpty()) {
            return unsatisfied(requirement, EvidenceExplanationService.notMentioned(requirement));
        }

        if (evidence.stream().anyMatch(this::isConflict)) {
            return new RequirementMatch(
                    requirement.index(), requirement.name(), requirement.normalizedName(),
                    requirement.category(), requirement.importance(),
                    MatchStatus.CONFLICT,
                    clamp(scoringConfiguration.getConflictConfidence()),
                    0, MatchProvenance.EXPLICIT,
                    displayEvidence(evidence), List.of(requirement.sourceText()),
                    EvidenceExplanationService.conflict(requirement));
        }

        CompoundSatisfaction satisfaction = satisfactionFor(requirement, evidence);
        EvidenceCandidate best = bestOf(requirement, evidence);
        MatchStatus status = classify(best, requirement, satisfaction);
        double confidence = confidenceFor(status, best, requirement, satisfaction);

        return new RequirementMatch(
                requirement.index(), requirement.name(), requirement.normalizedName(),
                requirement.category(), requirement.importance(),
                status, round(clamp(confidence)),
                best.strength().level(), provenanceFor(best, requirement, satisfaction), displayEvidence(evidence),
                List.of(requirement.sourceText()),
                EvidenceExplanationService.explain(requirement, status, best, shareOf(requirement, satisfaction)));
    }

    /**
     * The candidates strong enough to support a claim at all.
     *
     * <p>This is where "no evidence" stops meaning "the retrieval layer returned
     * nothing". A semantic index returns the nearest chunks whether or not they are
     * relevant - every resume has chunks and they are all somewhat wordy - so the
     * classifier, not the retriever, has to decide what counts. It does that by
     * strength: a candidate at {@link EvidenceStrength#WEAK} is a passage the engine
     * itself labelled adjacent-at-best, and it arrives as
     * {@link MatchProvenance#INFERRED}, meaning the engine connected it rather than
     * finding the requirement named.
     *
     * <p>Dropping them here rather than in {@link #classify} is deliberate. If they
     * were merely scored as zero, they would still be rendered on the report as the
     * "evidence" for a requirement the report simultaneously says was never
     * mentioned - a user has no way to tell that apart from a real finding. The
     * adjacent passage is not withheld from the candidate; it simply is not evidence,
     * and the recommendation list is where an unevidenced requirement still surfaces
     * as something to add.
     */
    private List<EvidenceCandidate> supporting(List<EvidenceCandidate> candidates) {
        if (candidates == null) {
            return List.of();
        }
        return candidates.stream().filter(c -> !isAdjacencyOnly(c)).toList();
    }

    /**
     * Evidence too weak to support any claim: inferred, and below contextual.
     *
     * <p>Only semantic hits reach {@code WEAK} - a cue is capped at contextual by
     * construction, and a direct mention is at least a listed skill - so this is
     * specifically the rule that stops low-similarity nearest-neighbour noise from
     * becoming partial credit.
     */
    private boolean isAdjacencyOnly(EvidenceCandidate candidate) {
        return candidate.provenance() == MatchProvenance.INFERRED
                && candidate.strength().level() < EvidenceStrength.CONTEXTUAL.level();
    }

    /**
     * The strongest evidence that counts as a direct mention of one of the
     * requirement's terms.
     *
     * <p>Filters out cue and semantic candidates first. Both are legitimate
     * evidence, but neither is the requirement being named, and letting a cue
     * promote a match to explicit is how "reduced response time by 30%" becomes a
     * claim about debugging.
     */
    private EvidenceCandidate bestOf(Requirement requirement, List<EvidenceCandidate> evidence) {
        List<EvidenceCandidate> direct = evidence.stream()
                .filter(c -> c.provenance() == MatchProvenance.EXPLICIT)
                .filter(c -> namesATermOf(requirement, c))
                .toList();
        List<EvidenceCandidate> pool = direct.isEmpty() ? evidence : direct;
        return pool.stream()
                .max((a, b) -> Integer.compare(a.strength().level(), b.strength().level()))
                .orElse(evidence.get(0));
    }

    /**
     * Whether a candidate was produced by a term the requirement actually
     * contains, rather than by a category member.
     */
    private boolean namesATermOf(Requirement requirement, EvidenceCandidate candidate) {
        for (NormalizedTerm term : requirement.normalization().terms()) {
            if (candidate.matchedOn() != null && candidate.matchedOn().equals(term.displayName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The state of a compound requirement: which of its terms found evidence.
     *
     * <p>Tracked per term because "JavaScript and/or TypeScript" and "Git and
     * GitHub" are the same shape and opposite rules - the first is satisfied by
     * either language, the second only by both.
     *
     * <p>Two sets, not one, because "the requirement was named" and "something in
     * the resume supports the requirement" are different claims and the classifier
     * needs both. A cue that fires on a real line is support for its own term only:
     * it says nothing about the other terms in the group.
     */
    private CompoundSatisfaction satisfactionFor(Requirement requirement, List<EvidenceCandidate> evidence) {
        List<NormalizedTerm> terms = requirement.normalization().terms();
        Set<String> named = new LinkedHashSet<>();
        Set<String> supported = new LinkedHashSet<>();
        for (NormalizedTerm term : terms) {
            boolean anySupporting = evidence.stream()
                    .anyMatch(c -> term.displayName().equals(c.matchedOn()));
            boolean directlyNamed = evidence.stream()
                    .filter(c -> c.provenance() == MatchProvenance.EXPLICIT)
                    .anyMatch(c -> term.displayName().equals(c.matchedOn())
                            && c.strength().level() >= EvidenceStrength.EXPLICIT_SKILL.level());
            if (anySupporting) {
                supported.add(term.canonicalKey());
            }
            if (directlyNamed) {
                named.add(term.canonicalKey());
            }
        }
        double ratio = terms.isEmpty() ? 0.0 : (double) named.size() / terms.size();
        return new CompoundSatisfaction(named, supported, ratio, terms.size());
    }

    /**
     * The match state, from evidence strength, the demand the job description
     * expressed, and whether the compound requirement was actually met.
     *
     * <p>Three rules, each closing a specific way of over-claiming:
     *
     * <ul>
     *   <li>The requirement's <em>shape</em> never mints evidence. Only evidence
     *       that survives {@link #supporting} gets here, and it is classified on its
     *       own strength. "Code reviews and software development activities" against
     *       a resume that never says either is silence, and a requirement that
     *       happens to contain the word "and" does not turn silence into a finding.</li>
     *   <li>A <b>conjunctive</b> compound with some terms named and others not is
     *       {@code PARTIAL_MATCH}, however strong the evidence for the part that is
     *       there. "Git" on a resume against "Git and GitHub" is half a finding.</li>
     *   <li>A <b>disjunctive</b> compound is met by its first evidenced term.
     *       "JavaScript and/or TypeScript" said one of the two is optional, and a
     *       requirement that ignores the operator the job description wrote down
     *       under-claims every candidate who met it.</li>
     * </ul>
     */
    private MatchStatus classify(EvidenceCandidate best, Requirement requirement,
                                 CompoundSatisfaction satisfaction) {
        if (satisfaction.underSatisfied(requirement)) {
            return MatchStatus.PARTIAL_MATCH;
        }
        MatchStatus byStrength = statusForStrength(best.strength(), requirement.demand());
        if (best.provenance() == MatchProvenance.INFERRED && byStrength == MatchStatus.EXPLICIT_MATCH) {
            return MatchStatus.STRONG_CONTEXTUAL_MATCH;
        }
        return byStrength;
    }

    private MatchStatus statusForStrength(EvidenceStrength strength, DemandLevel demand) {
        return switch (strength) {
            case DIRECT_PROFESSIONAL -> MatchStatus.EXPLICIT_MATCH;
            // A skill listed in a section is a claim, not a demonstration. It
            // answers a "familiarity" requirement, and only partly answers an
            // "advanced" one - which is the difference between "AWS" on a resume
            // and "Advanced AWS deployment".
            case EXPLICIT_SKILL -> demand == DemandLevel.ADVANCED
                    ? MatchStatus.PARTIAL_MATCH
                    : MatchStatus.EXPLICIT_MATCH;
            case CONTEXTUAL -> MatchStatus.STRONG_CONTEXTUAL_MATCH;
            case WEAK -> MatchStatus.PARTIAL_MATCH;
            case NONE -> MatchStatus.NOT_EXPLICITLY_MENTIONED;
        };
    }

    private double confidenceFor(MatchStatus status, EvidenceCandidate best, Requirement requirement,
                                CompoundSatisfaction satisfaction) {
        double base = switch (status) {
            case EXPLICIT_MATCH -> best.strength() == EvidenceStrength.DIRECT_PROFESSIONAL
                    ? scoringConfiguration.getExplicitMatchConfidence()
                    : scoringConfiguration.getListedSkillConfidence();
            case STRONG_CONTEXTUAL_MATCH -> scoringConfiguration.getStrongContextualConfidence();
            case PARTIAL_MATCH -> scoringConfiguration.getPartialMatchConfidence();
            case NOT_EXPLICITLY_MENTIONED -> 0.0;
            case CONFLICT -> scoringConfiguration.getConflictConfidence();
        };
        if (satisfaction.underSatisfied(requirement)) {
            // Scale by how much of the compound was found, but never to nothing:
            // the evidence that does exist is still evidence.
            base *= Math.max(scoringConfiguration.getCompoundPartialFloor(), satisfaction.satisfiedRatio());
        }
        if (best.provenance() == MatchProvenance.INFERRED) {
            // An inference the engine drew is weaker than a quotation, and says so.
            base *= 0.9;
        }
        return base;
    }

    private EvidenceExplanationService.CompoundShare shareOf(Requirement requirement,
                                                             CompoundSatisfaction satisfaction) {
        return EvidenceExplanationService.shareOf(requirement, satisfaction.named().size(),
                satisfaction.totalTerms(), satisfaction.underSatisfied(requirement));
    }

    private MatchProvenance provenanceFor(EvidenceCandidate best, Requirement requirement,
                                          CompoundSatisfaction satisfaction) {
        if (best.provenance() == MatchProvenance.INFERRED) {
            return MatchProvenance.INFERRED;
        }
        return satisfaction.underSatisfied(requirement) ? MatchProvenance.INFERRED : MatchProvenance.EXPLICIT;
    }

    /**
     * Whether the resume states the opposite of the requirement.
     *
     * <p>Worth distinguishing from silence. "No experience with Kubernetes" is a
     * statement; "no mention of Kubernetes" is the absence of one, and conflating
     * them would put a claim on someone's report that they never made.
     */
    private boolean isConflict(EvidenceCandidate candidate) {
        return candidate.provenance() == MatchProvenance.EXPLICIT
                && SynonymRegistry.negates(candidate.text());
    }

    private RequirementMatch unsatisfied(Requirement requirement, String explanation) {
        return new RequirementMatch(
                requirement.index(), requirement.name(), requirement.normalizedName(),
                requirement.category(), requirement.importance(),
                MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0,
                MatchProvenance.NOT_EXPLICIT, List.of(), List.of(requirement.sourceText()),
                explanation);
    }

    /**
     * The evidence sentences stored on the match.
     *
     * <p>Truncated here rather than during retrieval, so the cap is a
     * presentation choice and cannot change what the engine concluded.
     */
    private List<String> displayEvidence(List<EvidenceCandidate> evidence) {
        return evidence.stream()
                .limit(scoringConfiguration.getMaxEvidencePerRequirement())
                .map(EvidenceCandidate::text)
                .distinct()
                .toList();
    }

    // ---------------------------------------------------------------------
    // Experience
    // ---------------------------------------------------------------------

    /**
     * Compares a stated experience figure against the range the job asked for.
     *
     * <p>Arithmetic, because that is what it is: a band over a number, not a
     * phrase with evidence attached.
     *
     * <p>Exceeding the stated maximum is not a shortfall. A six-year candidate
     * against a "1-3 years" requirement is stronger than the band, not
     * mismatched by it, and scoring that as a deduction would be an engine
     * inventing a penalty the employer did not ask for.
     */
    public ExperienceAlignment alignExperience(ResumeProfile profile, ExperienceRequirement requirement) {
        Double stated = profile.statedYearsOfExperience();
        boolean wasStated = stated != null;
        Double candidateYears = wasStated ? stated : profile.inferredYearsOfExperience();

        if (requirement == null || (!requirement.hasMinimum() && !requirement.hasMaximum())) {
            return new ExperienceAlignment(candidateYears, wasStated, null, null,
                    ExperienceAlignment.Status.NOT_SPECIFIED, null,
                    candidateYears == null
                            ? "The job description does not state an experience requirement, and the resume "
                              + "states no experience either by figure or by dates."
                            : "The job description does not state an experience requirement, so this dimension "
                              + "is not scored.");
        }
        if (candidateYears == null) {
            return new ExperienceAlignment(null, false, requirement.minYears(), requirement.maxYears(),
                    ExperienceAlignment.Status.CANDIDATE_UNKNOWN, null,
                    "The job description asks for " + requirement.describe() + ", but the resume states no "
                            + "figure for total years of experience and gives no employment dates to derive "
                            + "one from, so this is not scored.");
        }

        String source = wasStated
                ? "The resume states " + describe(candidateYears) + " of experience"
                : "Employment dates on the resume total approximately " + describe(candidateYears)
                  + " of experience (estimated from the roles listed, not stated by the candidate)";

        double min = requirement.minYears() == null ? 0.0 : requirement.minYears();
        double max = requirement.maxYears() == null ? Double.MAX_VALUE : requirement.maxYears();

        if (candidateYears < min) {
            double ratio = min <= 0 ? 0
                    : Math.pow(candidateYears / min, scoringConfiguration.getExperienceShortfallExponent());
            double score = round(Math.max(0, Math.min(100, ratio * 100)));
            return new ExperienceAlignment(candidateYears, wasStated, requirement.minYears(),
                    requirement.maxYears(), ExperienceAlignment.Status.BELOW_RANGE, score,
                    source + " and the role asks for " + requirement.describe()
                            + ", so the candidate is below the stated minimum.");
        }
        if (candidateYears > max) {
            return new ExperienceAlignment(candidateYears, wasStated, requirement.minYears(),
                    requirement.maxYears(), ExperienceAlignment.Status.ABOVE_RANGE, 100.0,
                    source + ", above the stated " + requirement.describe()
                            + " range. This is additional experience rather than a shortfall.");
        }
        return new ExperienceAlignment(candidateYears, wasStated, requirement.minYears(),
                requirement.maxYears(), ExperienceAlignment.Status.WITHIN_RANGE, 100.0,
                source + ", which falls inside the " + requirement.describe()
                        + " range the role asks for.");
    }

    private String describe(double years) {
        if (years == Math.rint(years)) {
            return Long.toString((long) years) + " years";
        }
        return String.format(Locale.ROOT, "%.1f years", years);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(scoringConfiguration.getConfidenceCeiling(), value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * How much of a compound requirement the resume evidenced, term by term.
     *
     * @param named           terms the resume states outright
     * @param supported       terms with any surviving evidence, direct or indirect
     * @param satisfiedRatio fraction of terms named outright, 0..1
     * @param totalTerms      terms in the requirement
     */
    private record CompoundSatisfaction(Set<String> named, Set<String> supported,
                                        double satisfiedRatio, int totalTerms) {
        /** True when the requirement has more than one term. */
        boolean compound() { return totalTerms > 1; }
        boolean anySupported() { return !supported.isEmpty(); }

        /**
         * Whether the job description required every term and the resume left at
         * least one of them under-evidenced.
         *
         * <p>This is the <em>only</em> way a compound's shape can lower a match
         * state, and it reads the operator rather than assuming conjunction.
         *
         * <p>Three cases, and the two it deliberately excludes matter more than the
         * one it keeps:
         *
         * <ul>
         *   <li><b>Conjunction, some terms named and others not.</b> Partial, however
         *       strong the evidence for the part that is there. "Git" against "Git and
         *       GitHub" is half a finding.</li>
         *   <li><b>Conjunction, no term named but some supported by real indirect
         *       evidence.</b> Partial too, and for the same underlying reason: the
         *       group is conjunctive, one part has support, the other is silent.
         *       "Debugging and application performance" against a quantified caching
         *       improvement supports the performance half and says nothing about
         *       debugging, which is not the same claim as satisfying the whole
         *       responsibility.</li>
         *   <li><b>A disjunction with one term evidenced - not partial.</b>
         *       "JavaScript and/or TypeScript" is satisfied, because the employer
         *       wrote that one is enough. Reading it as a partly-met conjunction
         *       under-claims every candidate who met the requirement as written.</li>
         *   <li><b>A conjunction with nothing supported at all - not partial.</b>
         *       There is nothing to be partial about. "Code reviews and software
         *       development activities" against a resume that mentions neither is
         *       silence, and a requirement that happens to contain the word "and"
         *       must not turn silence into a finding with a confidence attached.</li>
         * </ul>
         */
        boolean underSatisfied(Requirement requirement) {
            if (!compound() || !requirement.normalization().requiresAllTerms()) {
                return false;
            }
            return named.isEmpty() ? anySupported() : satisfiedRatio < 1.0;
        }
    }
}
