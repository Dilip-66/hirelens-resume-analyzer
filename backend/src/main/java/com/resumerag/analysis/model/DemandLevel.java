package com.resumerag.analysis.model;

/**
 * How much capability the job description actually demands for a requirement.
 *
 * <p>The same resume evidence is worth a different amount against different
 * wording, and reading the qualifier off the requirement text is what stops a
 * skills-list mention being treated as equivalent to professional experience.
 * "AWS" on a resume against "Basic experience with AWS" is a match; the same
 * mention against "Advanced AWS deployment" is only partial, and without this the
 * two cases collapse into one.
 */
public enum DemandLevel {

    /** "Familiarity with X", "basic knowledge of X", "exposure to X". */
    BASIC,

    /** "Experience with X", "strong X", "design X", "optimize X". */
    PROFESSIONAL,

    /** "Advanced X", "expert in X", "deep expertise in X", "extensive X". */
    ADVANCED
}
