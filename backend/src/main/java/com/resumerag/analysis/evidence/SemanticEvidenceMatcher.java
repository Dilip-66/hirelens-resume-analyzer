package com.resumerag.analysis.evidence;

import java.util.List;
import java.util.UUID;

/**
 * Ranks resume passages by meaning rather than by keyword.
 *
 * <p>Exists because the alternative is asking a model to guess. A requirement
 * written as prose - "Participate in code reviews", "improve application
 * performance" - has no term to search for, and the honest way to establish
 * whether the resume says anything about it is to look for the passages closest
 * to it in meaning and see whether any of them clear a relevance bar.
 *
 * <p>Implemented over the resume's existing pgvector embeddings and the existing
 * embedding provider. No second embedding store, no second model, no parallel
 * index to keep in step with the first one.
 */
public interface SemanticEvidenceMatcher {

    /**
     * The semantic index for one resume.
     *
     * <p>Explicitly parameterised by resume rather than holding the resume in a
     * field: the matcher is a singleton, and a field would cross-contaminate two
     * concurrent analyses - one user quietly being scored against another user's
     * evidence.
     */
    Index indexFor(UUID resumeId);

    interface Index {

        /**
         * The resume passages most semantically similar to each requirement,
         * positionally aligned with the input.
         *
         * <p>Batched, because a job description has tens of requirements and one
         * embedding round trip per keyword is what turns a five-second analysis
         * into a minute of latency.
         */
        List<List<Hit>> rankAll(List<String> requirementTexts, int limit);

        List<Hit> rank(String requirementText, int limit);

        /**
         * A resume passage and how close it is in meaning to a requirement.
         *
         * @param similarity cosine similarity, clamped to 0..1
         */
        record Hit(String text, String section, double similarity) {}
    }
}
