package com.resumerag.retrieval;

import com.resumerag.algorithm.SkillTrie;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CandidateScorer {

    /**
     * How much a chunk's required-skill overlap can lift its score. Cosine
     * similarity lives in [-1, 1], so a fixed per-skill bonus would let a short
     * chunk that happens to name six skills outrank a genuinely better match.
     * Scaling the bonus by the number of required skills keeps the boost bounded
     * to MAX_SKILL_BOOST regardless of how long the job description is.
     */
    private static final double MAX_SKILL_BOOST = 0.15;

    private final SkillTrie skillTrie;

    public CandidateScorer(SkillTrie skillTrie) {
        this.skillTrie = skillTrie;
    }

    public double score(double similarity, Set<String> chunkSkills, Set<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty() || chunkSkills == null || chunkSkills.isEmpty()) {
            return similarity;
        }
        int matched = 0;
        for (String skill : requiredSkills) {
            if (chunkSkills.contains(skill)) {
                matched++;
            }
        }
        double coverage = (double) matched / requiredSkills.size();
        return similarity + (coverage * MAX_SKILL_BOOST);
    }

    /** Convenience overload for callers that only have raw chunk text. */
    public double score(double similarity, String chunkContent, Set<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty() || chunkContent == null) {
            return similarity;
        }
        return score(similarity, skillTrie.findAll(chunkContent), requiredSkills);
    }
}
