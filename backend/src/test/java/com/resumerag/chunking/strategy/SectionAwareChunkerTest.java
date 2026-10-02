package com.resumerag.chunking.strategy;

import com.resumerag.chunking.strategy.ChunkingStrategy.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectionAwareChunkerTest {

    private final SectionAwareChunker chunker = new SectionAwareChunker();

    private static final String RESUME = """
            John Doe
            john.doe@example.com

            SUMMARY
            Backend engineer with eight years of experience.

            TECHNICAL SKILLS: Java, Spring, PostgreSQL, AWS

            EXPERIENCE
            Senior Engineer at Acme (2021 - present)
            - Built payment services in Java on Spring Boot.
            - Migrated a monolith to PostgreSQL.

            EDUCATION
            B.Sc. Computer Science
            """;

    @Test
    void labelsEachChunkWithItsSection() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 800, 120);

        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("summary")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("technical skills")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("experience")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("education")));
    }

    @Test
    void keepsInlineHeaderValuesInTheSectionBody() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 800, 120);

        String skills = chunks.stream()
                .filter(c -> c.section().equals("technical skills"))
                .map(TextChunk::content)
                .reduce("", String::concat);

        // The list after "TECHNICAL SKILLS:" must survive, not be dropped.
        assertTrue(skills.contains("Java"), "inline header values were lost: " + skills);
        assertTrue(skills.contains("PostgreSQL"), "inline header values were lost: " + skills);
    }

    @Test
    void doesNotStraddleSectionBoundaries() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 800, 120);

        for (TextChunk chunk : chunks) {
            if (chunk.section().equals("education")) {
                assertFalse(chunk.content().contains("Spring Boot"),
                        "education chunk leaked experience content: " + chunk.content());
            }
        }
    }

    @Test
    void handlesBareHeaderLinesWithoutAColon() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 800, 120);

        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("summary")));
    }

    @Test
    void isCaseInsensitiveAboutHeaders() {
        String text = "work experience\nDid things.\n\nEDUCATION\nDegree.";

        List<TextChunk> chunks = chunker.chunk(text, 800, 120);

        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("work experience")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("education")));
    }

    @Test
    void labelsPreambleBeforeTheFirstHeaderAsUnnamed() {
        String text = "John Doe\njohn@example.com\n\nSUMMARY\nEngineer.";

        List<TextChunk> chunks = chunker.chunk(text, 800, 120);

        assertTrue(chunks.stream().anyMatch(c -> c.section().isEmpty()));
    }

    @Test
    void neverExceedsTheChunkSize() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 60, 10);

        assertTrue(chunks.stream().allMatch(c -> c.content().length() <= 60));
    }

    @Test
    void indexesAreContiguousAcrossSections() {
        List<TextChunk> chunks = chunker.chunk(RESUME, 60, 10);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }

    @Test
    void returnsNothingForEmptyInput() {
        assertTrue(chunker.chunk("", 800, 120).isEmpty());
        assertTrue(chunker.chunk("   ", 800, 120).isEmpty());
        assertTrue(chunker.chunk(null, 800, 120).isEmpty());
    }

    @Test
    void rejectsInvalidWindowParameters() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(RESUME, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(RESUME, 100, -1));
        // An overlap >= chunkSize would make the sliding window never advance.
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk(RESUME, 100, 100));
    }

    @Test
    void normalisesWindowsLineEndings() {
        String crlf = "SUMMARY\r\nEngineer.\r\n\r\nEDUCATION\r\nDegree.";

        List<TextChunk> chunks = chunker.chunk(crlf, 800, 120);

        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("summary")));
        assertTrue(chunks.stream().anyMatch(c -> c.section().equals("education")));
    }

    @Test
    void doesNotTreatOrdinaryProseAsAHeader() {
        String text = "SUMMARY\nI have experience in building things and leading teams.";

        List<TextChunk> chunks = chunker.chunk(text, 800, 120);

        assertEquals(1, chunks.size());
        assertEquals("summary", chunks.get(0).section());
    }
}
