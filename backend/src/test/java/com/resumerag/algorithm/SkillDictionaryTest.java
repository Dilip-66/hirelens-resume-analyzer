package com.resumerag.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the skill dictionary against the two failure modes that are invisible
 * in a unit test of any single skill: roles falling through the vocabulary
 * entirely, and one mention of a skill being counted twice.
 */
class SkillDictionaryTest {

    private final SkillTrie trie = dictionary();

    private static SkillTrie dictionary() {
        SkillTrie trie = new SkillTrie();
        trie.insertAll(SkillDictionary.SKILLS);
        return trie;
    }

    @Test
    void coversNonBackendRolesThatWerePreviouslyInvisible() {
        // A UI/UX resume used to match exactly one skill (Git), so the analysis
        // reported a near-total skill gap for a perfectly well-matched candidate.
        Set<String> design = trie.findAll("""
                UI/UX Designer with 3 years of experience.
                Skills: Figma, Photoshop, Illustrator, Canva, Sketch, Wireframing,
                Prototyping, Design Systems, Usability Testing, Branding.
                """);

        assertTrue(design.contains("Figma"));
        assertTrue(design.contains("Photoshop"));
        assertTrue(design.contains("UI/UX"));
        assertTrue(design.contains("Prototyping"));
        assertTrue(design.contains("Usability Testing"));
        assertTrue(design.size() >= 8, "expected broad design coverage, got " + design);
    }

    @Test
    void coversDataAndAnalyticsRoles() {
        Set<String> data = trie.findAll("""
                Data Analyst. Skills: Python, SQL, Excel, Power BI, Pandas, NumPy,
                Tableau, Statistics, A/B Testing, ETL, Data Visualization.
                """);

        assertTrue(data.contains("SQL"));
        assertTrue(data.contains("Power BI"));
        assertTrue(data.contains("Tableau"));
        assertTrue(data.contains("Pandas"));
        assertTrue(data.contains("ETL"));
        assertTrue(data.size() >= 9, "expected broad analytics coverage, got " + data);
    }

    @Test
    void matchesPluralisedSkillNames() {
        // "REST APIs" is how candidates actually write it; the singular entry
        // used to miss it entirely.
        assertTrue(trie.findAll("Built REST APIs in Java").contains("REST API"));
        assertTrue(trie.findAll("Managed multiple Kubernetes clusters").contains("Kubernetes"));
    }

    @Test
    void pluralRuleDoesNotFireOnWordsEndingInEs() {
        // "Go" must not be found inside "Goes".
        assertFalse(trie.findAll("She goes to the office").contains("Go"));
    }

    @Test
    void doesNotDoubleCountASkillMentionedUnderItsLongerName() {
        // "Tailwind" alongside "Tailwind CSS" would report two matches for one
        // mention and inflate the score.
        Set<String> found = trie.findAll("Built with Tailwind CSS and SASS");
        assertTrue(found.contains("Tailwind CSS"));
        assertFalse(found.contains("Tailwind"), "prefix-shadowing double count: " + found);
    }

    @Test
    void shortFormCatchesTheLongerPhrasesThatContainIt() {
        // "Spark" alone should still match "Apache Spark" at the word boundary,
        // so the dictionary only needs the shorter entry.
        assertTrue(trie.findAll("Built pipelines with Apache Spark").contains("Spark"));
        assertTrue(trie.findAll("Orchestration via Apache Airflow").contains("Airflow"));
    }

    @Test
    void sqlDoesNotFireInsideCompoundDatabaseNames() {
        Set<String> found = trie.findAll("Experienced with PostgreSQL and MySQL");
        assertFalse(found.contains("SQL"),
                "\"SQL\" inside \"PostgreSQL\"/\"MySQL\" is a false positive: " + found);
        assertTrue(found.contains("PostgreSQL"));
    }

    @Test
    void singleLetterSkillsAreNotInTheDictionary() {
        // Whole-word matching would score the "R" in "R&D" as the R language.
        assertFalse(SkillDictionary.SKILLS.contains("R"));
        assertFalse(SkillDictionary.SKILLS.contains("C"));
        assertFalse(trie.findAll("Worked in R&D and Sales").contains("R"));
    }

    @Test
    void longerSkillNamesAreNotDoubleCountedByTheirShorterPrefixes() {
        // "React" is a prefix of "React Native" and "Spring" of "Spring Boot".
        // Both are legitimate entries, so the trie resolves this with
        // longest-match-wins rather than by dropping either from the vocabulary.
        Set<String> mobile = trie.findAll("Built apps with React Native and TypeScript");
        assertTrue(mobile.contains("React Native"));
        assertFalse(mobile.contains("React"), "one mention of React Native counted twice: " + mobile);

        Set<String> backend = trie.findAll("Services built with Spring Boot and Java");
        assertTrue(backend.contains("Spring Boot"));
        assertFalse(backend.contains("Spring"), "one mention of Spring Boot counted twice: " + backend);
    }

    @Test
    void dictionaryHasNoDuplicates() {
        List<String> skills = SkillDictionary.SKILLS;
        assertEquals(skills.size(), Set.copyOf(skills).size(), "duplicate entries in the dictionary");
    }
}
