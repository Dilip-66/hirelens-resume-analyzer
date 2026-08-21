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
 * findNearestCandidates uses pgvector's cosine-distance operator (<=>) with
 * its ANN index (see supabase/schema.sql) to cheaply pull the N closest
 * candidates straight from the database. Final re-ranking to the true top-K
 * happens in Java via TopKHeap (see RetrievalService) so business-level
 * scoring (keyword overlap, section weighting) can be layered on afterwards.
 */
@Repository
public class ChunkVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(UUID chunkId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE resume_chunks SET embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), chunkId
        );
    }

    /** Returns chunk ids ordered by cosine distance ascending (closest first). */
    public List<CandidateRow> findNearestCandidates(UUID resumeId, float[] queryEmbedding, int limit) {
        String sql = """
            SELECT id, content, section, chunk_index, embedding <=> ?::vector AS distance
            FROM resume_chunks
            WHERE resume_id = ? AND embedding IS NOT NULL
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
        String literal = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new CandidateRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("content"),
                        rs.getString("section"),
                        rs.getInt("chunk_index"),
                        rs.getDouble("distance")
                ),
                literal, resumeId, literal, limit);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }

    public record CandidateRow(UUID chunkId, String content, String section, int chunkIndex, double distance) {}
}
