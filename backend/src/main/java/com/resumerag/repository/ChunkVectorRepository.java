package com.resumerag.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Talks to the pgvector "embedding" column on resume_chunks directly through
 * JDBC. Vectors are passed as the pgvector text literal format "[0.1,0.2,...]"
 * which Postgres casts with ::vector - this sidesteps needing a custom
 * Hibernate UserType for a single column.
 *
 * findNearestCandidates returns chunks ordered by cosine distance ascending
 * (closest first). Final re-ranking to the true top-K happens in Java via
 * TopKHeap (see RetrievalService) so business-level scoring can be layered on
 * afterwards.
 */
@Repository
public class ChunkVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(UUID chunkId, float[] embedding) {
        int updated = jdbcTemplate.update(
                "UPDATE resume_chunks SET embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), chunkId
        );
        if (updated == 0) {
            // Matching zero rows means the chunk row did not exist yet, so the
            // vector is silently lost and retrieval later returns no evidence.
            // Fail loudly instead of persisting a chunk with a NULL embedding.
            throw new IllegalStateException(
                    "No resume_chunks row with id " + chunkId + " to attach an embedding to. "
                            + "The chunk INSERT was probably not flushed before this update - "
                            + "use saveAndFlush rather than save.");
        }
    }

    /**
     * Returns candidate chunks for one resume, ordered by cosine distance
     * ascending (closest first).
     *
     * <p>This search is deliberately an <b>exact</b> KNN scan rather than an
     * ivfflat ANN scan. Retrieval is always scoped to a single resume, which is
     * a few dozen chunks at most, so the candidate set is far too small for
     * partitioning to pay off - and the ivfflat index created in
     * supabase/schema.sql ({@code lists = 100}) probes only one partition by
     * default, which means it silently returns zero rows for a small resume and
     * the analysis ends up with no evidence to reason over. The index is still in
     * the schema for when the search is widened beyond one resume.
     *
     * <p>The {@code OFFSET 0} is load-bearing: without it Postgres flattens the
     * subquery, sees a bare {@code ORDER BY embedding <=> ?} again and happily
     * uses the ivfflat index, reintroducing the empty-result bug.
     */
    public List<CandidateRow> findNearestCandidates(UUID resumeId, float[] queryEmbedding, int limit) {
        String sql = """
            SELECT id, content, section, chunk_index, distance
            FROM (
                SELECT id, content, section, chunk_index, embedding <=> ?::vector AS distance
                FROM resume_chunks
                WHERE resume_id = ? AND embedding IS NOT NULL
                OFFSET 0
            ) candidates
            ORDER BY distance ASC
            LIMIT ?
            """;
        String literal = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new CandidateRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("content"),
                        rs.getString("section"),
                        rs.getInt("chunk_index"),
                        rs.getDouble("distance")
                ),
                literal, resumeId, limit);
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding vector must not be empty");
        }
        StringBuilder sb = new StringBuilder(embedding.length * 10).append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    /**
     * Every embedded chunk of one resume, with its vector.
     *
     * <p>Used for per-requirement semantic matching, which needs to score a
     * requirement against a resume's chunks in bulk. Issuing a KNN query per
     * requirement instead would mean one database round trip per requirement -
     * around thirty for a typical job description - to compare vectors that are
     * already in memory as a few dozen rows. The index is not used here on
     * purpose: see {@link #findNearestCandidates} for why an ANN scan returns
     * nothing at this scale.
     *
     * <p>Rows with a NULL embedding are skipped rather than returned with an
     * empty vector, which would score as a similarity of 0 and read as "this
     * passage has nothing to do with the requirement" - a claim the data does
     * not support, since it was never embedded at all.
     */
    public List<ChunkWithVector> findWithEmbeddings(UUID resumeId) {
        String sql = """
                SELECT content, section, embedding::text AS embedding
                FROM resume_chunks
                WHERE resume_id = ? AND embedding IS NOT NULL
                ORDER BY chunk_index ASC
                """;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new ChunkWithVector(
                        rs.getString("content"),
                        rs.getString("section"),
                        parseVector(rs.getString("embedding"))),
                resumeId);
    }

    /**
     * Parses pgvector's text literal form "[0.1,0.2,...]" into a float array.
     *
     * <p>Returns an empty array for anything unreadable rather than throwing:
     * one unreadable row should cost that row's semantic evidence, not the whole
     * analysis, and the caller already skips vectors whose length does not match
     * the query.
     */
    private float[] parseVector(String literal) {
        if (literal == null || literal.isBlank()) {
            return new float[0];
        }
        String body = literal.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] values = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                return new float[0];
            }
        }
        return values;
    }

    public record CandidateRow(UUID chunkId, String content, String section, int chunkIndex, double distance) {}

    /** A chunk together with its embedding, for in-process similarity scoring. */
    public record ChunkWithVector(String content, String section, float[] embedding) {}
}
