package com.resumerag.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A piece of evidence the answer actually used.
 *
 * <p>Only emitted for context that was genuinely supplied to the model, so the
 * UI can never cite a section the model did not have.
 */
public record AiSource(String type, String section) {

    public static final String RESUME = "resume";
    public static final String JOB_DESCRIPTION = "jobDescription";
    public static final String ANALYSIS = "analysis";

    /** Not part of the wire format; present so the parser can validate. */
    @JsonIgnore
    public boolean isValidType() {
        return RESUME.equals(type) || JOB_DESCRIPTION.equals(type) || ANALYSIS.equals(type);
    }
}
