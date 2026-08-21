package com.resumerag.chunking.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Section-aware chunking strategy.
 * Detects common resume section headers and chunks within them.
 */
public class SectionAwareChunker implements ChunkingStrategy {

    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile(
        "(?i)^\\s*(experience|work experience|employment|education|skills|projects|summary|objective|certifications|awards|publications|references)\\s*:",
        Pattern.MULTILINE
    );

    @Override
    public List<TextChunk> chunk(String rawText, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return chunks;
        }

        // Split text by section headers
        String[] sections = rawText.split("(?m)^\\s*(?=(?:experience|work experience|employment|education|skills|projects|summary|objective|certifications|awards|publications|references)\\s*:)");
        
        int chunkIndex = 0;
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;

            // Extract section name if present
            String sectionName = "";
            if (trimmed.toLowerCase().matches("^(experience|work experience|employment|education|skills|projects|summary|objective|certifications|awards|publications|references)\\s*:.*")) {
                String[] parts = trimmed.split(":", 2);
                sectionName = parts[0].trim().toLowerCase();
                trimmed = parts[1].trim();
            }

            if (trimmed.isEmpty()) continue;

            // Apply sliding window on the section content
            if (trimmed.length() <= chunkSize) {
                chunks.add(new TextChunk(trimmed, sectionName, chunkIndex++));
            } else {
                int start = 0;
                while (start < trimmed.length()) {
                    int end = Math.min(start + chunkSize, trimmed.length());
                    String chunkContent = trimmed.substring(start, end);
                    chunks.add(new TextChunk(chunkContent, sectionName, chunkIndex++));
                    if (end == trimmed.length()) break;
                    start += chunkSize - overlap;
                }
            }
        }

        return chunks;
    }
}