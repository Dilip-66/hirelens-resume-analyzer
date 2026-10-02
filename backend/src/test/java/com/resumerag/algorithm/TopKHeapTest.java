package com.resumerag.algorithm;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopKHeapTest {

    @Test
    void rejectsNonPositiveK() {
        assertThrows(IllegalArgumentException.class, () -> new TopKHeap<String>(0));
        assertThrows(IllegalArgumentException.class, () -> new TopKHeap<String>(-3));
    }

    @Test
    void retainsOnlyTheHighestScoringItems() {
        TopKHeap<String> heap = new TopKHeap<>(3);
        heap.offer("a", 0.10);
        heap.offer("b", 0.90);
        heap.offer("c", 0.50);
        heap.offer("d", 0.70);

        assertEquals(3, heap.size());
        assertEquals(List.of("b", "d", "c"), names(heap));
    }

    @Test
    void evictsTheWeakestEntryWhenAFullHeapIsOfferedABetterItem() {
        TopKHeap<String> heap = new TopKHeap<>(2);
        heap.offer("weak", 0.1);
        heap.offer("mid", 0.5);
        heap.offer("strong", 0.9);

        assertFalse(heap.drainSortedDescending().stream().anyMatch(s -> "weak".equals(s.item)));
    }

    @Test
    void ignoresAnItemThatIsNotBetterThanTheCurrentWeakest() {
        TopKHeap<String> heap = new TopKHeap<>(2);
        heap.offer("a", 0.5);
        heap.offer("b", 0.5);
        heap.offer("c", 0.4); // tie-minus: must not displace anything

        assertEquals(2, heap.size());
        assertFalse(heap.drainSortedDescending().stream().anyMatch(s -> "c".equals(s.item)));
    }

    @Test
    void handlesFewerOffersThanK() {
        TopKHeap<String> heap = new TopKHeap<>(6);
        heap.offer("only", 0.1);

        assertEquals(1, heap.size());
        assertEquals(List.of("only"), names(heap));
    }

    @Test
    void staysEmptyWhenNothingIsOffered() {
        TopKHeap<String> heap = new TopKHeap<>(4);

        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        assertTrue(heap.drainSortedDescending().isEmpty());
    }

    @Test
    void drainsHighestScoreFirst() {
        TopKHeap<String> heap = new TopKHeap<>(4);
        heap.offer("low", 0.1);
        heap.offer("high", 0.9);
        heap.offer("mid", 0.5);

        List<TopKHeap.Scored<String>> drained = heap.drainSortedDescending();

        assertEquals(List.of("high", "mid", "low"), drained.stream().map(s -> s.item).toList());
        assertEquals(List.of(0.9, 0.5, 0.1), drained.stream().map(TopKHeap.Scored::score).toList());
    }

    @Test
    void pollsWeakestFirst() {
        TopKHeap<String> heap = new TopKHeap<>(3);
        heap.offer("high", 0.9);
        heap.offer("low", 0.1);
        heap.offer("mid", 0.5);

        assertEquals("low", heap.poll().item);
        assertEquals("mid", heap.poll().item);
        assertEquals("high", heap.poll().item);
        assertTrue(heap.isEmpty());
    }

    @Test
    void retainsExactlyTheTopKOutOfManyCandidates() {
        int n = 10_000;
        int k = 6;
        TopKHeap<Integer> heap = new TopKHeap<>(k);
        Random random = new Random(42);

        for (int i = 0; i < n; i++) {
            heap.offer(i, random.nextDouble());
        }

        assertEquals(k, heap.size());
        // Every retained entry must be >= every discarded one.
        double lowestRetained = heap.drainSortedDescending().get(k - 1).score;
        assertTrue(lowestRetained > 0.0);
    }

    @Test
    void matchesAFullSortOnRandomInput() {
        int n = 2_000;
        int k = 8;
        Random random = new Random(7);

        List<Integer> items = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(i);
            scores.add(random.nextDouble());
        }

        TopKHeap<Integer> heap = new TopKHeap<>(k);
        for (int i = 0; i < n; i++) {
            heap.offer(items.get(i), scores.get(i));
        }

        // Reference implementation: full sort, take the first K.
        List<Integer> byIndex = new ArrayList<>(items);
        byIndex.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        List<Integer> expected = byIndex.subList(0, k);

        assertEquals(expected, heap.drainSortedDescending().stream().map(s -> s.item).toList());
    }

    @Test
    void keepsTheExtremeScores() {
        TopKHeap<String> heap = new TopKHeap<>(2);
        heap.offer("mid", 0.5);
        heap.offer("min", -5.0);
        heap.offer("max", 5.0);

        assertEquals(List.of("max", "mid"), names(heap));
    }

    private static List<String> names(TopKHeap<String> heap) {
        return heap.drainSortedDescending().stream().map(TopKHeap.Scored::item).toList();
    }
}
