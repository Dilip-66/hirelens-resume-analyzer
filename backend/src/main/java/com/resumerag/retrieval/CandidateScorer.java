package com.resumerag.retrieval;

import com.resumerag.algorithm.SkillTrie;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class CandidateScorer {

    private final SkillTrie skillTrie;

    public CandidateScorer(SkillTrie skillTrie) {
        this.skillTrie = skillTrie;
    }

    public double score(float similarity, Set<String> chunkSkills, Set<String> requiredSkills) {
        int matchedSkillCount = 0;
        for (String skill : requiredSkills) {
            if (chunkSkills.contains(skill)) {
                matchedSkillCount++;
            }
        }
        return similarity + (matchedSkillCount * 0.03);
    }
}
