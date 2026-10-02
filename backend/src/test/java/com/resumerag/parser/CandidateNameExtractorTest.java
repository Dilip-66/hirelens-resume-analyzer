package com.resumerag.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandidateNameExtractorTest {

    private final CandidateNameExtractor extractor = new CandidateNameExtractor();

    @Test
    void readsTheNameFromTheFirstLine() {
        // Shaped like the real test resumes in ~/Downloads.
        String text = """
                Priya Sharma
                Frontend Developer • 1.5 years experience
                priya.sharma@example.com | (512) 555-0142

                SUMMARY
                Frontend-focused developer.
                """;

        assertEquals("Priya Sharma", extractor.extract(text, "Priya_Sharma_Resume.pdf"));
    }

    @Test
    void toleratesBlankLinesBeforeTheName() {
        String text = "\n\n   \nVikram Singh\nUI/UX Designer\n";
        assertEquals("Vikram Singh", extractor.extract(text, "Vikram_Singh_Resume.pdf"));
    }

    @Test
    void skipsADocumentTitleAndKeepsLooking() {
        // Some templates lead with the word "Resume" instead of the name.
        String text = """
                RESUME
                Arjun Rao
                Software Developer
                """;
        assertEquals("Arjun Rao", extractor.extract(text, "Arjun_Rao_Resume.pdf"));
    }

    @Test
    void skipsContactDetailsThatLeadTheDocument() {
        String text = """
                jordan.avery@example.com
                (512) 555-0142
                Jordan Avery
                Backend Engineer
                """;
        assertEquals("Jordan Avery", extractor.extract(text, "resume.pdf"));
    }

    @Test
    void readsALabelledName() {
        assertEquals("Rahul Kumar", extractor.extract("Name: Rahul Kumar\nBackend Developer", "x.pdf"));
    }

    @Test
    void fallsBackToTheFileNameWhenTheTextHasNoUsableName() {
        String text = "PROFESSIONAL SUMMARY\nBuilt things.";
        assertEquals("Priya Sharma", extractor.extract(text, "Priya_Sharma_Resume.pdf"));
    }

    @Test
    void derivesTheFileNameIntoATitleCasedName() {
        assertEquals("Arjun Rao", extractor.extract("", "arjun_rao_resume.pdf"));
        assertEquals("Sneha Patel", extractor.extract("   ", "Sneha-Patel_CV.pdf"));
    }

    @Test
    void neverReturnsBlankOrNull() {
        assertEquals("Unknown Candidate", extractor.extract(null, null));
        assertEquals("Unknown Candidate", extractor.extract("", ""));
    }

    @Test
    void rejectsAnOverlongFirstLine() {
        // A paragraph, not a name.
        String text = "This document sets out in considerable detail the entire history "
                + "of the organisation across many years of operation and change.";
        assertEquals("Sneha Patel", extractor.extract(text, "Sneha_Patel_Resume.pdf"));
    }

    @Test
    void doesNotConfuseTheLetterRWithTheRLanguage() {
        // Guards the sort of over-eager matching that produced false skills.
        assertEquals("Unknown Candidate",
                extractor.extract("R&D\nEngineering", "resume.pdf"));
    }
}
