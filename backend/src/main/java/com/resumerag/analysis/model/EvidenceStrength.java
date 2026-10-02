package com.resumerag.analysis.model;

/**
 * How strong the evidence behind a match is, on a 0-4 scale.
 *
 * <p>The scale is about the <em>kind</em> of evidence, not the amount of it,
 * because that is what a keyword count cannot see: a technology named once in a
 * skills list and the same technology used to ship something are both "one
 * mention", and they are not the same claim.
 *
 * <p>A quantified or outcome-bearing statement is credited one step higher than a
 * bare contextual hit, because "Reduced API response time by 30% through caching
 * and query optimization" is a demonstrated result rather than a hint - but it
 * still stops short of naming the requirement, which is what level 4 means.
 */
public enum EvidenceStrength {

    /** Nothing in the resume speaks to this requirement. */
    NONE(0),

    /**
     * Adjacent at best. A single weak cue, or a low-similarity semantic
     * neighbour. Enough to justify a suggestion, never enough to claim a match.
     */
    WEAK(1),

    /**
     * The same capability in different words: the engine recognised the
     * connection from context, and a reader could reasonably agree.
     */
    CONTEXTUAL(2),

    /**
     * The resume names the capability and shows it in use, or states it plainly
     * in a way that carries a demonstrated outcome - a listed skill, or a
     * quantified result in a role.
     */
    EXPLICIT_SKILL(3),

    /**
     * The resume uses the capability in professional work. The strongest claim a
     * resume can support, and reserved for that: "Dockerized microservices and
     * deployed applications on AWS EC2 and S3" is not the same as "AWS" in a
     * skills list, and a report that cannot tell them apart is not explainable.
     */
    DIRECT_PROFESSIONAL(4);

    private final int level;

    EvidenceStrength(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public static EvidenceStrength ofLevel(int level) {
        for (EvidenceStrength strength : values()) {
            if (strength.level == level) {
                return strength;
            }
        }
        return NONE;
    }
}
