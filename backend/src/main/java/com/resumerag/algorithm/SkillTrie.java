package com.resumerag.algorithm;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SkillTrie {
    private final TrieNode root = new TrieNode();

    public void insert(String skill) {
        TrieNode node = root;
        for (char c : skill.toLowerCase().toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEnd = true;
        node.skill = skill;
    }

    public void insertAll(Collection<String> skills) {
        skills.forEach(this::insert);
    }

    public Set<String> findAll(String text) {
        Set<String> found = new HashSet<>();
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            TrieNode node = root;
            int j = i;
            while (j < lower.length()) {
                node = node.children.get(lower.charAt(j));
                if (node == null) break;
                if (node.isEnd) {
                    if (isWordBoundary(text, i, j + 1)) {
                        found.add(node.skill);
                    }
                }
                j++;
            }
        }
        return found;
    }

    private boolean isWordBoundary(String text, int start, int end) {
        boolean startOk = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return startOk && endOk;
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
        String skill = null;
    }
}
