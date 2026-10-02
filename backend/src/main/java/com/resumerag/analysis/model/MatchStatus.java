package com.resumerag.analysis.model;

/**
 * How the resume measures up to one requirement.
 *
 * <p>The previous pipeline had two states - present or absent - which is why a
 * single keyword hit in a skills list read identically to five years of shipping
 * production software, and why a genuinely weaker-but-real answer looked like a
 * total miss. Four states separate the cases that actually occur.
 *
 * <p>Note what {@link #NOT_EXPLICITLY_MENTIONED} does NOT mean: it is not a claim
 * that the candidate lacks the skill. A resume is a document, and silence in a
 * document is only evidence about the document. The report wording reflects that
 * throughout.
 */
public enum MatchStatus {

    /**
     * The resume demonstrates the requirement directly - stated as a skill, or
     * used in a way that leaves no other reading (e.g. "Dockerized
     * microservices" for "Experience with Docker").
     */
    EXPLICIT_MATCH,

    /**
     * The exact wording differs but the resume gives real supporting evidence -
     * the same capability arrived at through different language.
     */
    STRONG_CONTEXTUAL_MATCH,

    /**
     * Some real evidence exists but it falls short of what the job asked for,
     * either because the demand was higher (a listed skill against an "advanced"
     * requirement) or because a compound requirement was only half satisfied.
     */
    PARTIAL_MATCH,

    /**
     * The resume provides no evidence either way.
     *
     * <p>Reported as "not explicitly mentioned", never as a lack of ability.
     */
    NOT_EXPLICITLY_MENTIONED,

    /**
     * The resume states the opposite of the requirement, e.g. lists the skill
     * under a "learning" heading. Rare, and scored as no evidence at all.
     */
    CONFLICT
}
