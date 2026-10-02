package com.resumerag.config;

import com.resumerag.algorithm.SkillDictionary;
import com.resumerag.algorithm.SkillTrie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the hand-written algorithm types (SkillTrie) as singletons so the
 * dictionary is built exactly once and shared by every component that needs it
 * instead of each component doing `new SkillTrie()` (which would leave the
 * trie empty unless it also remembered to insert the dictionary).
 */
@Configuration
public class AlgorithmConfig {

    @Bean
    public SkillTrie skillTrie() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(SkillDictionary.SKILLS);
        return trie;
    }
}
