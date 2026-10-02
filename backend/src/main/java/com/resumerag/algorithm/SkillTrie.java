package com.resumerag.algorithm;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SkillTrie {
    private final TrieNode root = new TrieNode();

    public void insert(String skill) {
        if (skill == null || skill.isBlank()) {
            return;
        }
        TrieNode node = root;
        for (char c : skill.toLowerCase(Locale.ROOT).toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        // Keep the first-inserted canonical name for entries that share a prefix
        // ("Java" vs "JavaScript"): blindly re-assigning here lets a later
        // insert rename a terminal that was already registered.
        if (!node.isEnd) {
            node.isEnd = true;
            node.skill = skill;
        }
    }

    public void insertAll(Collection<String> skills) {
        skills.forEach(this::insert);
    }

    public Set<String> findAll(String text) {
        if (text == null || text.isEmpty()) {
            return new LinkedHashSet<>();
        }
        // Locale.ROOT keeps the lowercase mapping 1:1 with the original string, so
        // the indices below stay aligned with `text` (default-locale lowercasing
        // can change a character's length and desync the boundary check).
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < lower.length(); i++) {
            TrieNode node = root;
            int j = i;
            // Longest match wins for a given start position. Without this, a
            // mention of "React Native" reports both "React" and "React Native"
            // (likewise "Spring Boot" and "Spring"), counting one skill twice
            // and inflating the match score.
            String match = null;
            while (j < lower.length()) {
                node = node.children.get(lower.charAt(j));
                if (node == null) break;
                if (node.isEnd && (isWordBoundary(text, i, j + 1) || isPluralBoundary(text, i, j + 1))) {
                    match = node.skill;
                }
                j++;
            }
            if (match != null) {
                found.add(match);
            }
        }
        return found;
    }

    private boolean isWordBoundary(String text, int start, int end) {
        boolean startOk = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return startOk && endOk;
    }

    /**
     * Accepts a single trailing plural "s" followed by a real boundary, so the
     * dictionary entry "REST API" still matches the far more common "REST APIs"
     * on a resume. Without this, pluralised skill names were silently invisible:
     * every candidate who wrote "REST APIs" scored as having no API experience.
     *
     * <p>Exactly one "s", and only when the character after it is a boundary.
     * That keeps it from turning "Go" into a match for "Goes" (which has "es",
     * not a bare "s") while still matching "APIs" and "Kuberneteses".
     */
    private boolean isPluralBoundary(String text, int start, int end) {
        if (end >= text.length() || text.charAt(end) != 's') {
            return false;
        }
        int afterPlural = end + 1;
        boolean startOk = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean endOk = afterPlural == text.length() || !Character.isLetterOrDigit(text.charAt(afterPlural));
        return startOk && endOk;
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
        String skill = null;
    }
}
