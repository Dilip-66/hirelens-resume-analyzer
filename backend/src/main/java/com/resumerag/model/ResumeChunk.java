package com.resumerag.model;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * NOTE: the pgvector "embedding" column deliberately isn't mapped here.
 * Writing/reading vector literals is done through raw JDBC in
 * ChunkVectorRepository, which keeps the pgvector wire format (e.g.
 * "[0.01,0.02,...]") out of the JPA layer and avoids a fragile custom
 * Hibernate UserType for a single column.
 */
@Entity
@Table(name = "resume_chunks")
public class ResumeChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "section")
    private String section;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    public ResumeChunk() {}

    public ResumeChunk(UUID resumeId, int chunkIndex, String section, String content) {
        this.resumeId = resumeId;
        this.chunkIndex = chunkIndex;
        this.section = section;
        this.content = content;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getResumeId() { return resumeId; }
    public void setResumeId(UUID resumeId) { this.resumeId = resumeId; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
