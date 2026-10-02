package com.resumerag.chunking.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Section-aware chunking strategy.
 *
 * <p>Resumes are overwhelmingly organised under a small set of known headers.
 * Keeping a chunk's section label lets the retrieval layer report which part of
 * the resume a piece of evidence came from, and splitting at header boundaries
 * stops a chunk from straddling two unrelated sections.
 *
 * <p>Two header spellings are handled, because both are common and the previous
 * colon-only pattern missed the second one entirely (leaving the whole resume as a
 * single unnamed section): a bare header line ("WORK EXPERIENCE") and an inline
 * header ("Technical Skills: Java, Python, AWS"), whose trailing values are kept as
 * the first line of the section body rather than being dropped.
 */
public class SectionAwareChunker implements ChunkingStrategy {

    private static final String HEADER_ALTERNATION =
            "experience|work experience|professional experience|employment|employment history|"
            + "education|education & training|academic background|skills|technical skills|core competencies|"
            + "projects|personal projects|summary|professional summary|objective|profile|"
            + "certifications|certifications & licenses|awards|achievements|publications|"
            + "references|volunteer experience|internships";

    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile(
            "^[ \\t]*(" + HEADER_ALTERNATION + ")[ \\t]*(?::[ \\t]*(.*))?$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public List<TextChunk> chunk(String rawText, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return chunks;
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must be non-negative");
        }
        if (overlap >= chunkSize) {
            // The sliding window advances by (chunkSize - overlap); an overlap at or
            // above chunkSize would never terminate.
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }

        int chunkIndex = 0;
        for (Section section : splitIntoSections(rawText)) {
            chunkIndex = window(section.body, section.name, chunkSize, overlap, chunks, chunkIndex);
        }

        return chunks;
    }

    private List<Section> splitIntoSections(String rawText) {
        List<Section> sections = new ArrayList<>();
        String currentName = "";
        StringBuilder currentBody = new StringBuilder();

        String[] lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (String line : lines) {
            Matcher header = SECTION_HEADER_PATTERN.matcher(line.trim());
            if (header.matches()) {
                if (!currentBody.toString().isBlank()) {
                    sections.add(new Section(currentName, currentBody.toString().trim()));
                }
                currentName = header.group(1).trim().toLowerCase();
                currentBody.setLength(0);
                String inlineContent = header.group(2);
                if (inlineContent != null && !inlineContent.isBlank()) {
                    currentBody.append(inlineContent.trim());
                }
            } else {
                if (currentBody.length() > 0) {
                    currentBody.append('\n');
                }
                currentBody.append(line);
            }
        }
        if (!currentBody.toString().isBlank()) {
            sections.add(new Section(currentName, currentBody.toString().trim()));
        }
        return sections;
    }

    private int window(String body, String sectionName, int chunkSize, int overlap,
                       List<TextChunk> chunks, int startIndex) {
        int index = startIndex;
        int start = 0;
        while (start < body.length()) {
            int end = Math.min(start + chunkSize, body.length());
            String content = body.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(content, sectionName, index++));
            }
            if (end == body.length()) {
                break;
            }
            start += chunkSize - overlap;
        }
        return index;
    }

    private record Section(String name, String body) {}
}
