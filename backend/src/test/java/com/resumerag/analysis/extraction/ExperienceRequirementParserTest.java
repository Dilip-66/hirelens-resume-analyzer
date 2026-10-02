package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.ExperienceRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Years of experience, read from the ways job descriptions actually write it.
 *
 * <p>The separator is the whole difficulty. "1-3 years" with a hyphen, with an
 * en dash, and with the word "to" are the same requirement, and a parser that
 * misses the en dash loses the single most load-bearing number in a job
 * description - and does so silently, leaving the candidate with no experience
 * score and no error.
 */
class ExperienceRequirementParserTest {

    private final ExperienceRequirementParser parser = new ExperienceRequirementParser(java.time.Clock.systemUTC());

    @Test
    @DisplayName("Every separator spelling of a range is the same range")
    void rangeSeparatorsAreEquivalent() {
        for (String text : List.of("1-3 years", "1\u20133 years", "1\u20143 years", "1 to 3 years",
                "1 - 3 years", "Experience: 1-3 years")) {
            ExperienceRequirement requirement = parser.parsePrimary(text);
            assertNotNull(requirement, "no constraint read from: " + text);
            assertEquals(1.0, requirement.minYears(), "wrong minimum in: " + text);
            assertEquals(3.0, requirement.maxYears(), "wrong maximum in: " + text);
        }
    }

    @Test
    @DisplayName("An open-ended minimum has no ceiling")
    void openEndedMinimumsHaveNoMaximum() {
        ExperienceRequirement plus = parser.parsePrimary("2+ years of experience");
        assertEquals(2.0, plus.minYears());
        assertNull(plus.maxYears(), "'2+ years' means at least two, not exactly two");

        for (String text : List.of("minimum 3 years", "at least 3 years", "more than 3 years",
                "min 5 years experience", "over 4 years")) {
            ExperienceRequirement requirement = parser.parsePrimary(text);
            assertNotNull(requirement, "no constraint read from: " + text);
            assertNotNull(requirement.minYears());
            assertTrue(requirement.minYears() >= 3, "wrong minimum read from: " + text);
        }
    }

    @Test
    @DisplayName("A reversed range is ordered rather than discarded")
    void reversedRangesAreOrdered() {
        ExperienceRequirement requirement = parser.parsePrimary("3-2 years of experience");
        assertNotNull(requirement);
        assertEquals(2.0, requirement.minYears());
        assertEquals(3.0, requirement.maxYears());
    }

    @Test
    @DisplayName("The same band written twice is one constraint")
    void theSameBandIsDeduplicated() {
        List<ExperienceRequirement> found = parser.parseAll("""
                Experience: 1\u20133 years

                Required Skills:
                * 1\u20133 years of software development experience
                """);

        assertEquals(1, found.size(),
                "one number stated twice must not become two constraints; otherwise a single band can "
                        + "be read as a requirement the candidate has to evidence twice");
    }

    @Test
    @DisplayName("A requirement with no year range is not a constraint")
    void nonNumericExperienceIsNotAConstraint() {
        assertNull(parser.parsePrimary("Experience with Java and Spring Boot"));
        assertNull(parser.parsePrimary("Familiarity with Git"));
        assertNull(parser.parsePrimary(""));
        assertNull(parser.parsePrimary(null));
    }

    @Test
    @DisplayName("A candidate's stated years are read from the resume")
    void candidateYearsAreRead() {
        assertEquals(2.5, parser.parseCandidateYears(
                "Software Developer with 2.5 years of experience building web applications."));
        assertEquals(6.0, parser.parseCandidateYears(
                "Experience: 6 years"));
        assertEquals(3.0, parser.parseCandidateYears("3+ years of software development experience"));
    }

    @Test
    @DisplayName("A resume that states no figure yields null, not a guess")
    void anUnstatedFigureIsNull() {
        assertNull(parser.parseCandidateYears("Software Developer at Acme, 2021 to 2024"));
        assertNull(parser.parseCandidateYears(""));
        assertNull(parser.parseCandidateYears(null));
    }

    @Test
    @DisplayName("Employment dates yield a figure when the resume states none")
    void datesYieldAFigureWhenNoneIsStated() {
        assertEquals(2.0, parser.parseYearsFromDates("Engineer — Beta | 2022-01 to 2024-01"), 0.1);

        Double acrossTwoRoles = parser.parseYearsFromDates(
                "Software Developer — Acme | 2022–Present\nEngineer — Beta | 2020–2022");
        Double oneContinuousRole = parser.parseYearsFromDates("Engineer — Beta | 2020–Present");
        assertEquals(oneContinuousRole, acrossTwoRoles, 0.1,
                "two roles that meet end to end are one span of calendar time");
    }

    @Test
    @DisplayName("Overlapping roles count once, not twice")
    void concurrentRolesAreCountedOnce() {
        Double summed = parser.parseYearsFromDates(
                "Consultant — Alpha | 2020–2023\nContractor — Beta | 2020–2023");

        assertEquals(3.0, summed, 0.1,
                "two contracts running at once are three years of calendar experience, not six. "
                        + "Adding them is the easiest way to inflate a CV without lying");
    }

    @Test
    @DisplayName("A separate gap is not experience")
    void aGapBetweenRolesIsNotCounted() {
        assertEquals(4.0, parser.parseYearsFromDates(
                        "Engineer — Beta | 2022–2023\nEngineer — Gamma | 2010–2013"),
                0.1,
                "the eight years between the two roles are years the candidate was not in them");
    }

    @Test
    @DisplayName("Dates alone cannot produce an unbounded figure")
    void anOpenEndedRangeIsCapped() {
        Double years = parser.parseYearsFromDates("Engineer — Beta | 2015–Present");

        assertNotNull(years);
        assertTrue(years <= 90.0,
                "an open-ended range runs from whenever it started to now, and without a ceiling a "
                        + "typo in a year could imply a century of experience: " + years);
    }

    @Test
    @DisplayName("A stated figure is used as-is, and the dates are not consulted")
    void aStatedFigureBeatsTheDates() {
        assertEquals(2.5, parser.parseCandidateYears(
                        "Software Developer with 2.5 years of experience\nEngineer — Beta | 2015–Present"),
                "the candidate's own number wins; the dates are arithmetic over the document, not "
                        + "a statement by its author");
    }

    @Test
    @DisplayName("A range renders the way a person would say it")
    void rangesRenderReadably() {
        assertEquals("1-3 years", new ExperienceRequirement(1.0, 3.0, "1-3 years").describe());
        assertEquals("2+ years", new ExperienceRequirement(2.0, null, "2+ years").describe());
        assertEquals("3 years", new ExperienceRequirement(3.0, 3.0, "3 years").describe());
        assertEquals("up to 2 years", new ExperienceRequirement(null, 2.0, "x").describe());
    }
}
