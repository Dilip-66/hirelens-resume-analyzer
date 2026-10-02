package com.resumerag.chunking.strategy;

import com.resumerag.chunking.strategy.ChunkingStrategy.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedSizeChunkerTest {

    private final FixedSizeChunker chunker = new FixedSizeChunker();

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    @Test
    void splitsTextIntoSequentialChunks() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 250), 100, 20);

        assertEquals(3, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        assertEquals(2, chunks.get(2).index());
    }

    @Test
    void leavesNoSectionLabel() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 250), 100, 20);

        assertTrue(chunks.stream().allMatch(c -> c.section().isEmpty()));
    }

    @Test
    void neverExceedsTheChunkSize() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 1000), 100, 20);

        assertTrue(chunks.stream().allMatch(c -> c.content().length() <= 100));
    }

    @Test
    void overlapsConsecutiveChunks() {
        // size 10, overlap 4 -> each window advances by 6.
        List<TextChunk> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz", 10, 4);

        String first = chunks.get(0).content();
        String second = chunks.get(1).content();

        assertTrue(second.startsWith(first.substring(first.length() - 4)),
                "second chunk should re-open with the previous chunk's tail");
    }

    @Test
    void returnsASingleChunkWhenTextFits() {
        List<TextChunk> chunks = chunker.chunk("short resume", 800, 120);

        assertEquals(1, chunks.size());
        assertEquals("short resume", chunks.get(0).content());
    }

    @Test
    void handlesTextExactlyOneChunkLong() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 100), 100, 20);

        assertEquals(1, chunks.size());
    }

    @Test
    void returnsNothingForEmptyInput() {
        assertTrue(chunker.chunk("", 100, 20).isEmpty());
        assertTrue(chunker.chunk("   ", 100, 20).isEmpty());
        assertTrue(chunker.chunk(null, 100, 20).isEmpty());
    }

    @Test
    void trimsSurroundingWhitespace() {
        List<TextChunk> chunks = chunker.chunk("  padded  ", 100, 20);

        assertEquals("padded", chunks.get(0).content());
    }

    @Test
    void rejectsInvalidWindowParameters() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("text", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("text", -5, 10));
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("text", 100, -1));
        // An overlap >= chunkSize would make the sliding window never advance.
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("text", 100, 100));
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("text", 100, 200));
    }

    @Test
    void terminatesOnZeroOverlap() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 500), 100, 0);

        assertEquals(5, chunks.size());
    }

    @Test
    void indexesAreContiguous() {
        List<TextChunk> chunks = chunker.chunk(repeat('a', 777), 100, 30);

        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
        }
    }
}
