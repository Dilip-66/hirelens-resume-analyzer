package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.DemandLevel;
import com.resumerag.analysis.model.EvidenceCandidate;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;

/**
 * Writes the one-sentence reason that accompanies every match state.
 *
 * <p>The wording is the product, not decoration. A report that says a candidate
 * "cannot do code reviews" on the strength of a resume that simply does not
 * mention them is making a claim the evidence does not support, and a candidate
 * reading their own report has no way to tell that apart from a real finding.
 *
 * <p>So: silence is reported as silence. The engine says what it did and did not
 * see, names the sentence it based that on, and never converts an absence into a
 * deficiency. Every phrase here is a claim about the document.
 */
public final class EvidenceExplanationService {

    private EvidenceExplanationService() {}

    /** The wording for a requirement with no supporting evidence. */
    public static String notMentioned(Requirement requirement) {
        StringBuilder sb = new StringBuilder("Not explicitly mentioned in the resume.");
        if (requirement.category() == RequirementCategory.PREFERRED_SKILL) {
            sb.append(" This is a preferred skill, so it carries little weight in the score.");
        } else if (requirement.category() == RequirementCategory.RESPONSIBILITY) {
            sb.append(" The job description expects this, but the resume does not provide direct evidence "
                    + "of this experience.");
        } else if (requirement.category() == RequirementCategory.REQUIRED_SKILL) {
            sb.append(" The job description asks for it, but the resume does not provide direct evidence "
                    + "of it.");
        }
        sb.append(" This is a statement about what the resume says, not about what the candidate can do.");
        return sb.toString();
    }

    /**
     * The wording for a requirement the resume actively contradicts.
     *
     * <p>Distinct from silence on purpose, and still not a claim about ability:
     * the resume itself framed the requirement as something the candidate does
     * not have.
     */
    public static String conflict(Requirement requirement) {
        return "The resume refers to " + requirement.name() + " in terms that suggest it is not yet something "
                + "the candidate has worked with directly, rather than as a demonstrated capability.";
    }

    /**
     * The wording for a matched requirement.
     *
     * @param best the evidence the classification was based on
     */
    public static String explain(Requirement requirement, MatchStatus status, EvidenceCandidate best,
                                 CompoundShare share) {
        String quote = best == null ? "" : "\"" + shorten(best.text()) + "\"";
        return switch (status) {
            case EXPLICIT_MATCH -> explicit(requirement, best, quote);
            case STRONG_CONTEXTUAL_MATCH -> contextual(requirement, best, quote, share);
            case PARTIAL_MATCH -> partial(requirement, best, quote, share);
            case CONFLICT -> conflict(requirement);
            case NOT_EXPLICITLY_MENTIONED -> notMentioned(requirement);
        };
    }

    private static String explicit(Requirement requirement, EvidenceCandidate best, String quote) {
        if (best != null && best.strength() == com.resumerag.analysis.model.EvidenceStrength.DIRECT_PROFESSIONAL) {
            return "The resume directly demonstrates " + requirement.name()
                    + " in professional work: " + quote + ".";
        }
        if (requirement.demand() == DemandLevel.ADVANCED) {
            return "The resume lists " + requirement.name() + " as a skill, while the role asks for advanced "
                    + "experience with it, so this is counted as partial evidence: " + quote + ".";
        }
        return "The resume states " + requirement.name() + " as a skill: " + quote + ".";
    }

    private static String contextual(Requirement requirement, EvidenceCandidate best, String quote,
                                     CompoundShare share) {
        StringBuilder sb = new StringBuilder("The resume provides related evidence for " + requirement.name());
        if (best != null && best.strength() == com.resumerag.analysis.model.EvidenceStrength.EXPLICIT_SKILL) {
            sb.append(", showing the capability in use rather than naming the requirement: ");
        } else {
            sb.append(" through different wording: ");
        }
        sb.append(quote).append(".");
        if (share.partial() && share.anyNamed()) {
            sb.append(" Only part of this requirement is evidenced: ")
              .append(share.namedCount()).append(" of ").append(share.totalCount())
              .append(" parts were found.");
        }
        return sb.toString();
    }

    private static String partial(Requirement requirement, EvidenceCandidate best, String quote,
                                 CompoundShare share) {
        StringBuilder sb = new StringBuilder();
        if (share.partial() && share.anyNamed()) {
            sb.append("Part of this requirement is evidenced and part is not: ")
              .append(share.namedCount()).append(" of ").append(share.totalCount())
              .append(" parts were found (").append(quote).append(").");
            return sb.toString();
        }
        if (share.partial()) {
            // No term of the group was named outright; something related was found
            // for part of it and nothing for the rest. Saying "0 of 2 parts were
            // found" would read as though nothing was found at all.
            sb.append("Part of this requirement has related evidence and part of it is not mentioned at all")
              .append(": ").append(quote).append(".");
            return sb.toString();
        }
        if (requirement.demand() == DemandLevel.ADVANCED
                && best != null
                && best.strength() == com.resumerag.analysis.model.EvidenceStrength.EXPLICIT_SKILL) {
            sb.append(requirement.name()).append(" appears on the resume, but as a listed skill rather than ")
              .append("as demonstrated work, and the role asks for advanced experience: ")
              .append(quote).append(".");
            return sb.toString();
        }
        if (best == null || best.strength() == com.resumerag.analysis.model.EvidenceStrength.WEAK) {
            sb.append("Only indirect evidence was found for ").append(requirement.name())
              .append(", which is not enough to count it as a match.");
            return sb.toString();
        }
        sb.append("The resume shows related work for ").append(requirement.name())
          .append(", but not at the level the role asks for: ").append(quote).append(".");
        return sb.toString();
    }

    /**
     * How much of a conjunctive requirement was evidenced, for the explanation.
     *
     * <p>Declared here rather than reusing the matching service's private record so
     * the explanation layer does not depend on the classifier's internals.
     *
     * @param namedCount       terms the resume stated outright
     * @param totalCount       terms in the requirement
     * @param conjunctiveGap   whether the job description required every term and at
     *                         least one was left under-evidenced. Decided by the
     *                         classifier rather than re-derived here, so the sentence
     *                         can never disagree with the state it is explaining.
     *                         A disjunction is never a gap: telling a user "only 1 of
     *                         2 parts were found" about a requirement the job
     *                         description wrote with "or" is a statement about the
     *                         requirement, not about the resume - and it is wrong.
     */
    public record CompoundShare(int namedCount, int totalCount, boolean conjunctiveGap) {
        /** True when the requirement was written as a conjunction and is only partly met. */
        public boolean partial() {
            return conjunctiveGap;
        }

        /** True when at least one term was named outright, so a count is meaningful. */
        public boolean anyNamed() {
            return namedCount > 0;
        }
    }

    /**
     * How much of a compound requirement the evidence covered, read through the
     * operator the job description actually used.
     *
     * <p>Package-visible for the matching service, which already knows the
     * per-term state and should not re-derive it.
     */
    static CompoundShare shareOf(Requirement requirement, int named, int total, boolean conjunctiveGap) {
        if (total <= 1) {
            return new CompoundShare(0, 0, false);
        }
        return new CompoundShare(named, total, conjunctiveGap);
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 220 ? trimmed : trimmed.substring(0, 217) + "...";
    }

    /** True when a line reads as a denial rather than an absence. */
    static boolean isNegated(String text) {
        return SynonymRegistry.negates(text);
    }
}
