package com.resumerag.analysis.evidence;

import com.resumerag.analysis.model.EvidenceStrength;

import java.util.List;

/**
 * A resume read as evidence, rather than as a blob of text.
 *
 * <p>Split into lines and tagged with the section each came from, because where a
 * technology appears is the difference between a claim and a demonstration.
 * "AWS" under Technical Skills is a claim; "deployed applications on AWS EC2 and
 * S3" inside a role is a demonstration. A pipeline that reads the whole resume as
 * one string cannot tell them apart, which is why a single keyword has always
 * been able to produce a full-strength match.
 *
 * <p>Kept as a value object so the evidence layer can be tested against fixed
 * fixtures without a database, a model, or a document parser.
 */
public record ResumeProfile(
        List<Line> lines,
        Double statedYearsOfExperience,
        Double inferredYearsOfExperience
) {

    public ResumeProfile {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /**
     * One meaningful line of the resume, tagged with where it came from.
     *
     * <p>{@code baseStrength} is the ceiling this line can support. It is not a
     * claim that the line satisfies anything - a line only counts when it
     * actually mentions the requirement - but it does mean a skills list can
     * never produce professional-strength evidence no matter how the text reads,
     * because nothing in a list of technologies shows anybody doing the work.
     */
    public record Line(String text, Section section, EvidenceStrength baseStrength) {

        /**
         * Where on a resume this line sat.
         *
         * <p>The order matters only for readability; the evidence strength each
         * section can support is what the engine actually uses.
         */
        public enum Section {
            /** "Professional Experience", "Employment History", dated roles. */
            WORK,
            /** Built and shipped things. Real work, so treated like a role. */
            PROJECTS,
            /** "Technical Skills", "Technologies", "Core Competencies". */
            SKILLS,
            /** The candidate's own summary of themselves. */
            SUMMARY,
            EDUCATION,
            CERTIFICATIONS,
            /** Anything we could not place - the header block, contact details. */
            OTHER
        }
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public List<Line> linesIn(Line.Section section) {
        return lines.stream().filter(l -> l.section() == section).toList();
    }
}
