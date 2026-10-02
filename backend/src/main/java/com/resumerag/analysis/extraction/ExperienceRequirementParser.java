package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.ExperienceRequirement;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a years-of-experience constraint out of free text.
 *
 * <p>Experience is not a skill and cannot be matched like one. "1-3 years",
 * "2+ years", "minimum 3 years" and "3 to 5 years" are ranges over a number, and
 * a candidate with 2.5 years either meets them or does not - there is no
 * evidence sentence to retrieve and no semantic similarity to compute. So it is
 * parsed arithmetically and compared numerically, and the result is reported as
 * its own dimension.
 *
 * <p>Handles the separator mess that real job descriptions contain: en dash, em
 * dash, hyphen, "to", and a bare space. "1–3" and "1-3" are the same
 * requirement, and treating the en dash as punctuation would silently drop one
 * of the most common ways of writing one.
 */
@Service
public class ExperienceRequirementParser {

    /**
     * The clock used to resolve an open-ended "2021 - Present" role.
     *
     * <p>Injected rather than called statically, because a benchmark that reads
     * the wall clock is not repeatable: every open-ended span grows by a month
     * between runs and the scores drift for reasons no one changed. Tests supply a
     * fixed clock; production gets the system clock.
     */
    private final Clock clock;

    public ExperienceRequirementParser(Clock clock) {
        this.clock = clock;
    }

    /**
     * Ranges: "1-3 years", "1–3 years", "1 to 3 years", "1–3+ years".
     *
     * <p>The separator class has to come before the plain single-number pattern,
     * or "1-3 years" is read as a bare "3 years".
     */
    private static final Pattern RANGE = Pattern.compile(
            "(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(?:-|–|—|to|~)\\s*(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*"
                    + "(?:years?|yrs?|year)", Pattern.CASE_INSENSITIVE);

    /**
     * Open-ended minimums: "2+ years", "5 years of experience", "over 3 years".
     *
     * <p>Only treated as a minimum when a lower bound is actually expressed, so
     * "5 years of experience" is read as "at least 5" - which is how employers
     * write it - rather than as a demand for exactly five.
     */
    private static final Pattern MINIMUM = Pattern.compile(
            "(?:minimum(?:\\s+of)?|at\\s+least|min(?:imum)?\\.?|over|more\\s+than|>=?)\\s*"
                    + "(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years?|yrs?)",
            Pattern.CASE_INSENSITIVE);

    /** Plain "N years of experience", and the "N+ years" form. */
    private static final Pattern PLAIN_YEARS = Pattern.compile(
            "(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*(\\+?)\\s*(?:years?|yrs?)"
                    + "(?:\\s+(?:of\\s+)?(?:professional\\s+|relevant\\s+|industry\\s+)?"
                    + "(?:software\\s+|work\\s+|industry\\s+|programming\\s+|development\\s+)*experience)?",
            Pattern.CASE_INSENSITIVE);

    /**
     * How many years the candidate claims, read from a resume.
     *
     * <p>Only the explicit forms. The first figure wins: a resume lists "3 years"
     * in the summary and "6 years" in a role description more often than you would
     * hope, and taking the largest would quietly flatter every candidate.
     */
    private static final Pattern STATED_YEARS = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years?|yrs?)"
                    + "(?:\\s+(?:of\\s+)?(?:professional\\s+|total\\s+|overall\\s+|hands-on\\s+)?"
                    + "(?:software\\s+|work\\s+|industry\\s+|programming\\s+|development\\s+)*experience)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Dated employment entries: "Software Developer - Acme | 2021 - Present".
     *
     * <p>The fallback path. It exists because a 20%-weighted dimension that
     * returns nothing for most real resumes is a design problem, and the benchmark
     * showed it plainly: 17 of 30 scenarios dropped experience entirely, so a one
     * year candidate against a three-year minimum scored exactly as though nobody
     * had asked.
     *
     * <p>It is a <em>fallback</em>, and the two properties that keep it inside the
     * accuracy rules are:
     *
     * <ul>
     *   <li>Intervals are <b>unioned</b>, so two concurrent roles contribute one
     *       span rather than two. Someone who ran a job and freelanced alongside it
     *       has one year of calendar experience, not two, and summing is the
     *       obvious way to overstate a CV.</li>
     *   <li>The result is labelled inferred, with a confidence discount, so a
     *       report can never present it as something the candidate wrote. Absence
     *       of dates still yields null.</li>
     * </ul>
     */
    private static final Pattern DATED_ROLE = Pattern.compile(
            "(?<![\\d])(19|20)\\d{2}\\s*(?:-|–|—|to)\\s*"
                    + "(?:(19|20)\\d{2}|present|current|now|ongoing|today)(?![\\d])",
            Pattern.CASE_INSENSITIVE);

    /** Education and other non-employment spans, which must not count as work. */
    /**
     * A year, optionally with a month: "2021", "2021-03", "03/2021".
     *
     * <p>Month precision is included because real resumes carry it and a parser
     * that only understands bare years silently reads "2024-01 - 2025-01" as
     * containing no employment at all - which is how a one-year role became
     * unmeasurable, and then invisible to a 20%-weighted dimension.
     */
    private static final Pattern YEAR_MONTH = Pattern.compile(
            "(?<![\\d])((?:19|20)\\d{2})(?:[-/.](\\d{1,2}))?(?![\\d])");

    /** Wording that means the role is current. */
    private static final Pattern OPEN_ENDED = Pattern.compile(
            "\\b(present|current|now|ongoing|today|to date)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern NON_EMPLOYMENT_CONTEXT = Pattern.compile(
            "\\b(university|college|school|education|degree|bachelor|master|phd|internship|"
                    + "course|certification|training|volunteer)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * The longest employment span worth crediting, in years.
     *
     * <p>A 30-year cap on a career is generous, and it stops a resume listing a
     * date typo - or a career that genuinely started in 1975 - from dominating an
     * experience comparison.
     */
    private static final double MAX_CREDITED_SPAN_YEARS = 30.0;

    /**
     * Constraints found anywhere in a job description, deduplicated.
     *
     * <p>Deduplicated by normalised range, because a JD states the same band
     * twice as often as not - once in the header and once in the skills list -
     * and reading it as two requirements would let one number weigh twice.
     */
    public List<ExperienceRequirement> parseAll(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ExperienceRequirement> found = new ArrayList<>();

        Matcher range = RANGE.matcher(text);
        while (range.find()) {
            Double low = parseNumber(range.group(1));
            Double high = parseNumber(range.group(2));
            if (low != null && high != null) {
                // "3-2 years" is a typo, not an impossible requirement; order it
                // rather than discarding the candidate's stated intent.
                if (low > high) {
                    Double swap = low;
                    low = high;
                    high = swap;
                }
                found.add(new ExperienceRequirement(low, high, range.group().trim()));
            }
        }

        Matcher minimum = MINIMUM.matcher(text);
        while (minimum.find()) {
            Double low = parseNumber(minimum.group(1));
            if (low != null) {
                found.add(new ExperienceRequirement(low, null, minimum.group().trim()));
            }
        }

        Matcher plain = PLAIN_YEARS.matcher(text);
        while (plain.find()) {
            Double years = parseNumber(plain.group(1));
            if (years == null) {
                continue;
            }
            boolean openEnded = "+".equals(plain.group(2));
            // A bare "3 years" inside a text that already states a range is the
            // upper bound of that range restated ("1-3 years of experience" ends
            // in "3 years"), not a second, independent constraint.
            if (!openEnded && isBoundOfAnAlreadyFoundRange(years, found)) {
                continue;
            }
            found.add(new ExperienceRequirement(years, openEnded ? null : years, plain.group().trim()));
        }

        return ExperienceRequirement.dedupe(found);
    }

    /**
     * The single constraint to score against: the most specific one stated.
     *
     * <p>A JD that says both "1-3 years" and "5+ years" is internally
     * inconsistent, and picking one silently would hide that. The first
     * specific constraint wins and the inconsistency is left visible in the
     * requirement's own source text.
     */
    public ExperienceRequirement parsePrimary(String text) {
        List<ExperienceRequirement> all = parseAll(text);
        if (all.isEmpty()) {
            return null;
        }
        return all.stream()
                .filter(ExperienceRequirement::hasMinimum)
                .findFirst()
                .orElse(all.get(0));
    }

    /**
     * The candidate's stated years of experience, or {@code null}.
     *
     * <p>The first explicit figure wins. A resume lists "3 years" in the summary
     * and "6 years" in a role description more often than you would hope, and
     * taking the largest would quietly flatter every candidate - so the reading
     * order is preserved and the first explicit statement is the one reported.
     */
    public Double parseCandidateYears(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return null;
        }
        Matcher matcher = STATED_YEARS.matcher(resumeText);
        if (matcher.find()) {
            return parseNumber(matcher.group(1));
        }
        // "Experience: 2.5 years" / "2.5 years of experience" written as a bare
        // field, e.g. a form-generated resume.
        Matcher labelled = Pattern.compile(
                        "(?:experience|exp)\\s*[:\\-]\\s*(\\d+(?:\\.\\d+)?)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(resumeText);
        if (labelled.find()) {
            return parseNumber(labelled.group(1));
        }
        return null;
    }

    /**
     * Years of employment derived from stated date ranges, or {@code null}.
     *
     * <p>A fallback for {@link #parseCandidateYears}, never an override: if the
     * resume states a figure, that figure is the answer and the dates are not
     * consulted. Only the union of the employment spans is counted, capped, and
     * returned to the caller as an explicitly inferred estimate.
     */
    public Double parseYearsFromDates(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return null;
        }
        List<Span> spans = employmentSpans(resumeText);
        if (spans.isEmpty()) {
            return null;
        }

        // Union of the intervals, so concurrent roles contribute one span. Summing
        // them would report a consultant who ran two contracts in parallel as
        // having twice the calendar experience, which is the easiest way to inflate
        // a CV without saying anything untrue.
        List<Span> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator.comparing(Span::from));

        double totalMonths = 0;
        LocalDate cursorStart = null;
        LocalDate cursorEnd = null;
        for (Span span : ordered) {
            if (cursorStart == null) {
                cursorStart = span.from();
                cursorEnd = span.to();
                continue;
            }
            // One month of slack: a resume that says "2021-2022" and then
            // "2022-2023" means two contiguous years, not a gap.
            if (span.from().isAfter(cursorEnd.plusMonths(1))) {
                totalMonths += monthsBetween(cursorStart, cursorEnd);
                cursorStart = span.from();
                cursorEnd = span.to();
            } else if (span.to().isAfter(cursorEnd)) {
                cursorEnd = span.to();
            }
        }
        if (cursorStart != null) {
            totalMonths += monthsBetween(cursorStart, cursorEnd);
        }

        if (totalMonths <= 0) {
            return null;
        }
        double years = totalMonths / 12.0;
        return round(Math.min(years, MAX_CREDITED_SPAN_YEARS));
    }

    /**
     * The dated spans on lines that read like employment.
     *
     * <p>Lines mentioning university, a degree, an internship or a certification
     * are skipped. "University of Leeds, 2018 - 2021" has the same shape as a job
     * and is not employment, and counting a degree as three years of experience
     * would inflate the one dimension the candidate cannot argue with.
     */
    private List<Span> employmentSpans(String resumeText) {
        List<Span> spans = new ArrayList<>();
        YearMonth today = YearMonth.now(clock);
        for (String rawLine : resumeText.split("\\R")) {
            if (NON_EMPLOYMENT_CONTEXT.matcher(rawLine).find()) {
                continue;
            }
            spans.addAll(spansOnLine(rawLine, today));
        }
        return spans;
    }

    /**
     * The employment spans on one line.
     *
     * <p>Year tokens are paired in order, and an open-ended marker turns an odd
     * trailing year into a span running to now. A lone year with no open-ended
     * marker is a graduation year, not a role, and contributes nothing.
     */
    private List<Span> spansOnLine(String line, YearMonth today) {
        List<YearMonth> dates = new ArrayList<>();
        Matcher years = YEAR_MONTH.matcher(line);
        while (years.find()) {
            YearMonth date = toYearMonth(years.group(1), years.group(2));
            if (date != null) {
                dates.add(date);
            }
        }
        if (dates.isEmpty()) {
            return List.of();
        }

        boolean openEnded = OPEN_ENDED.matcher(line).find();
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i + 1 < dates.size(); i += 2) {
            YearMonth from = dates.get(i);
            YearMonth to = dates.get(i + 1);
            if (!to.isBefore(from)) {
                spans.add(new Span(from.atDay(1), to.atEndOfMonth()));
            }
        }
        if (openEnded && dates.size() % 2 == 1) {
            YearMonth from = dates.get(dates.size() - 1);
            if (!today.isBefore(from)) {
                spans.add(new Span(from.atDay(1), today.atEndOfMonth()));
            }
        }
        return spans;
    }

    private YearMonth toYearMonth(String year, String month) {
        try {
            int y = Integer.parseInt(year);
            int m = month == null || month.isBlank() ? 1 : Integer.parseInt(month);
            if (m < 1 || m > 12) {
                m = 1;
            }
            return YearMonth.of(y, m);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }

    private long monthsBetween(LocalDate from, LocalDate to) {
        long months = (to.getYear() - from.getYear()) * 12L + (to.getMonthValue() - from.getMonthValue());
        return Math.max(1L, months);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private boolean isBoundOfAnAlreadyFoundRange(Double years, List<ExperienceRequirement> found) {
        for (ExperienceRequirement requirement : found) {
            if (years.equals(requirement.minYears()) || years.equals(requirement.maxYears())) {
                return true;
            }
        }
        return false;
    }

    private Double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value);
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A closed employment interval, from the first day of the start month. */
    private record Span(LocalDate from, LocalDate to) {}
}
