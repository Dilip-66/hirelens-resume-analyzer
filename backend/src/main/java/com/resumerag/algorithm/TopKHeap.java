package com.resumerag.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Bounded min-heap that keeps only the K highest-scoring items it is offered.
 *
 * <p>Why a heap instead of sorting: sorting N candidates costs O(N log N) and
 * materialises the whole list, whereas keeping a size-K heap costs O(N log K)
 * time and O(K) memory - the right trade when N is the candidate pool and K is a
 * small fixed number (typically 6).
 *
 * <p>Because the queue is a min-heap, {@link #peek()} is the current <em>weakest</em>
 * entry, which is exactly the one to evict when a better candidate arrives. The
 * natural poll order is therefore ascending (weakest first), so callers that want
 * relevance-ordered output must use {@link #drainSortedDescending()}.
 */
public class TopKHeap<T> {

    private static final Comparator<Scored<?>> ASCENDING =
            Comparator.comparingDouble((Scored<?> s) -> s.score);

    private final int k;
    private final PriorityQueue<Scored<T>> heap;

    public TopKHeap(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive, was " + k);
        }
        this.k = k;
        this.heap = new PriorityQueue<>(Math.min(k, 16), ASCENDING);
    }

    public void offer(T item, double score) {
        if (heap.size() < k) {
            heap.add(new Scored<>(item, score));
        } else if (heap.peek() != null && score > heap.peek().score) {
            heap.poll();
            heap.add(new Scored<>(item, score));
        }
    }

    public Scored<T> poll() {
        return heap.poll();
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /** Drains the retained top-K ordered from highest score to lowest. */
    public List<Scored<T>> drainSortedDescending() {
        List<Scored<T>> result = new ArrayList<>(heap);
        result.sort(Comparator.comparingDouble((Scored<T> s) -> s.score).reversed());
        return result;
    }

    public int size() {
        return heap.size();
    }

    public static final class Scored<T> {
        public final T item;
        public final double score;

        public Scored(T item, double score) {
            this.item = item;
            this.score = score;
        }

        public T item() {
            return item;
        }

        public double score() {
            return score;
        }
    }
}
