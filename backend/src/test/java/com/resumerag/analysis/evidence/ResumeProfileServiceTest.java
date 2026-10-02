package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.model.EvidenceStrength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * How a resume is read into evidence.
 *
 * <p>Each test here protects one specific way a flattened resume produces a
 * wrong answer: a technology named once in a skills list scoring the same as one
 * used to ship something, a sentence that merely mentions "experience" being
 * mistaken for the heading and upgrading everything below it to professional
 * strength, and a date-derived experience figure being presented as one the
 * candidate stated.
 */
class ResumeProfileServiceTest {

    private final ResumeProfileService service = new ResumeProfileService(new ExperienceRequirementParser(java.time.Clock.systemUTC()));

    @Test
    @DisplayName("A technology used in a role outranks the same technology listed as a skill")
    void professionalUseOutranksASkillList() {
        ResumeProfile profile = service.build("""
                Technical Skills:
                * Docker
                * AWS

                Experience:
                Software Engineer \u2014 Acme | 2023\u2013Present
                * Dockerized microservices and deployed applications on AWS EC2 and S3.
                """);

        ResumeProfile.Line inRole = profile.lines().stream()
                .filter(l -> l.text().contains("Dockerized"))
                .findFirst().orElseThrow();
        ResumeProfile.Line inList = profile.lines().stream()
                .filter(l -> l.text().equals("Docker"))
                .findFirst().orElseThrow();

        assertEquals(EvidenceStrength.DIRECT_PROFESSIONAL, inRole.baseStrength());
        assertEquals(EvidenceStrength.EXPLICIT_SKILL, inList.baseStrength(),
                "a skills list is a claim about a person; no phrasing in a list of technologies turns it "
                        + "into a description of work");
    }

    @Test
    @DisplayName("A sentence mentioning experience is not the experience heading")
    void aSentenceMentioningExperienceIsNotAHeading() {
        ResumeProfile profile = service.build("""
                Summary:
                Software Developer with 2.5 years of experience building scalable applications.
                * Developed REST APIs using Java and Spring Boot.
                """);

        // Both lines stay in the summary, so neither is promoted to professional
        // strength. Treating the sentence as the heading would upgrade everything
        // after it - which is how an unlabelled bullet becomes "demonstrated in
        // professional work".
        assertEquals(ResumeProfile.Line.Section.SUMMARY, profile.lines().get(0).section());
        assertEquals(ResumeProfile.Line.Section.SUMMARY, profile.lines().get(1).section());
    }

    @Test
    @DisplayName("A dated role line opens a work block even with no heading")
    void aDatedRoleLineImpliesWork() {
        ResumeProfile profile = service.build("""
                Jordan Lee
                Software Developer \u2014 Acme | 2022\u2013Present
                * Built internal services.
                """);

        ResumeProfile.Line bullet = profile.lines().stream()
                .filter(l -> l.text().contains("internal services"))
                .findFirst().orElseThrow();
        assertEquals(ResumeProfile.Line.Section.WORK, bullet.section());
        assertEquals(EvidenceStrength.DIRECT_PROFESSIONAL, bullet.baseStrength());
    }

    @Test
    @DisplayName("Conventional headings are recognised")
    void conventionalHeadingsAreRecognised() {
        ResumeProfile profile = service.build("""
                Work Experience:
                * Built services.

                Technical Skills:
                * Java

                Education:
                * BSc Computer Science
                """);

        assertEquals(ResumeProfile.Line.Section.WORK, profile.lines().get(0).section());
        assertEquals(ResumeProfile.Line.Section.SKILLS, profile.lines().get(1).section());
        assertEquals(ResumeProfile.Line.Section.EDUCATION, profile.lines().get(2).section());
    }

    @Test
    @DisplayName("A contact block is not mistaken for a section")
    void aContactBlockIsNotASection() {
        ResumeProfile profile = service.build("""
                Arjun Rao
                arjun@example.com | +44 7700 900000 | London
                """);

        for (ResumeProfile.Line line : profile.lines()) {
            assertEquals(ResumeProfile.Line.Section.OTHER, line.section());
        }
    }

    @Test
    @DisplayName("The stated years of experience are read, and not invented")
    void statedYearsAreReadNotInvented() {
        assertEquals(2.5, service.build("Summary:\nDeveloper with 2.5 years of experience.").statedYearsOfExperience());
        assertNotNull(service.build("Experience:\n2.5 years").statedYearsOfExperience());

        // No figure stated means null. Deriving one from employment dates is an
        // assumption about overlapping roles, and this field is read as something
        // the candidate said.
        org.junit.jupiter.api.Assertions.assertNull(
                service.build("Experience:\nSoftware Developer \u2014 Acme | 2021\u2013Present").statedYearsOfExperience());
    }

    @Test
    @DisplayName("An empty resume yields no lines and no figure")
    void anEmptyResumeYieldsNothing() {
        ResumeProfile profile = service.build("   ");
        assertEquals(0, profile.lines().size());
        org.junit.jupiter.api.Assertions.assertNull(profile.statedYearsOfExperience());
    }
}
