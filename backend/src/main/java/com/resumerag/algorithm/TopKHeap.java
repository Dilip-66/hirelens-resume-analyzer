package com.resumerag.algorithm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class TopKHeap<T> {
    private final int k;
    private final PriorityQueue<Scored<T>> heap;

    public TopKHeap(int k) {
        this.k = k;
        this.heap = new PriorityQueue<>(k, Comparator.comparingDouble(s -> s.score));
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

        public double score() {
            return score;
        }
    }
}
