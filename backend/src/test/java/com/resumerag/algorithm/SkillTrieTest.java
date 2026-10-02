package com.resumerag.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTrieTest {

    @Test
    void findsSkillsInPlainText() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(List.of("Java", "Python", "AWS"));

        Set<String> found = trie.findAll("Built services in Java and deployed on AWS.");

        assertEquals(Set.of("Java", "AWS"), found);
    }

    @Test
    void matchesCaseInsensitively() {
        SkillTrie trie = new SkillTrie();
        trie.insert("PostgreSQL");

        assertEquals(Set.of("PostgreSQL"), trie.findAll("expert in postgresql and POSTGRESQL"));
    }

    @Test
    void returnsTheCanonicalCasingOfTheInsertedSkill() {
        SkillTrie trie = new SkillTrie();
        trie.insert("React");

        assertEquals(Set.of("React"), trie.findAll("react and REACT"));
    }

    @Test
    void requiresWholeWordBoundaries() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Go");

        // "Go" must not match inside "Google" or "Mongo".
        assertTrue(trie.findAll("Google Cloud").isEmpty());
        assertTrue(trie.findAll("MongoDB").isEmpty());
        assertEquals(Set.of("Go"), trie.findAll("I write Go."));
    }

    @Test
    void distinguishesPrefixesThatShareAStem() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(List.of("Java", "JavaScript"));

        assertEquals(Set.of("JavaScript"), trie.findAll("JavaScript only"));
        assertEquals(Set.of("Java"), trie.findAll("Java only"));
        assertEquals(Set.of("Java", "JavaScript"), trie.findAll("Java and JavaScript"));
    }

    @Test
    void keepsTheFirstInsertedNameForSharedPrefixes() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Java");
        trie.insert("JavaScript");

        // "Java" is the terminal registered first and must not be renamed.
        assertEquals(Set.of("Java"), trie.findAll("Java"));
        assertEquals(Set.of("JavaScript"), trie.findAll("JavaScript"));
    }

    @Test
    void handlesEmptyAndNullInput() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Java");

        assertTrue(trie.findAll("").isEmpty());
        assertTrue(trie.findAll(null).isEmpty());
        assertTrue(new SkillTrie().findAll("nothing here").isEmpty());
    }

    @Test
    void ignoresBlankInserts() {
        SkillTrie trie = new SkillTrie();
        trie.insert(null);
        trie.insert("");
        trie.insert("   ");

        assertTrue(trie.findAll("Java").isEmpty());
    }

    @Test
    void deduplicatesRepeatedOccurrences() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Docker");

        Set<String> found = trie.findAll("Docker, Docker, DOCKER, docker");

        assertEquals(1, found.size());
    }

    @Test
    void findsSkillsAdjacentToPunctuation() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(List.of("C++", "Node.js", "CI/CD"));

        assertEquals(Set.of("C++", "Node.js", "CI/CD"),
                trie.findAll("Stack: C++, Node.js, CI/CD pipelines."));
    }

    @Test
    void detectsHyphenatedAndSlashedOccurrences() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Vue");
        trie.insert("CI/CD");

        assertEquals(Set.of("Vue", "CI/CD"), trie.findAll("Built with Vue-based tooling and a CI/CD pipeline"));
    }

    @Test
    void scansMultilineResumeText() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(List.of("Spring", "Kafka", "Terraform"));

        String resume = """
                SUMMARY
                Backend engineer focused on distributed systems.

                EXPERIENCE
                Built Spring services on Kafka.
                Provisioned infrastructure with Terraform.
                """;

        assertEquals(Set.of("Spring", "Kafka", "Terraform"), trie.findAll(resume));
    }

    @Test
    void findsNothingWhenNoSkillIsPresent() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(List.of("Rust", "Scala"));

        assertTrue(trie.findAll("A resume with no listed skills.").isEmpty());
    }

    @Test
    void insertionOrderDoesNotAffectTheResult() {
        SkillTrie a = new SkillTrie();
        a.insertAll(List.of("Java", "Spring"));

        SkillTrie b = new SkillTrie();
        b.insertAll(List.of("Spring", "Java"));

        assertEquals(a.findAll("Java and Spring"), b.findAll("Java and Spring"));
    }

    @Test
    void doesNotReportASubstringMatchInsideALongerWord() {
        SkillTrie trie = new SkillTrie();
        trie.insert("Script");

        assertFalse(trie.findAll("TypeScript").contains("Script"));
        assertTrue(trie.findAll("TypeScript").isEmpty());
    }
}
