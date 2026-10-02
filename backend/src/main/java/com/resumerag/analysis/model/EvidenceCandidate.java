package com.resumerag.analysis.model;

/**
 * A piece of resume text that bears on a requirement, with the engine's reading
 * of how good it is.
 *
 * <p>Kept as its own type so the evidence layer can rank candidates for a
 * requirement without deciding anything about the match - retrieval and
 * classification stay separately testable, and the sentence that won is always
 * recoverable.
 */
public record EvidenceCandidate(
        String text,
        String section,
        EvidenceStrength strength,
        MatchProvenance provenance,
        double similarity,
        String matchedOn
) {

    public EvidenceCandidate {
        if (similarity < 0.0) {
            similarity = 0.0;
        } else if (similarity > 1.0) {
            similarity = 1.0;
        }
    }

    public boolean isStrongerThan(EvidenceCandidate other) {
        if (other == null) {
            return strength.level() > 0;
        }
        return strength.level() > other.strength.level();
    }
}
