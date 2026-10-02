package com.resumerag.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the benchmark corpus from disk.
 *
 * <p>One JSON file per scenario, in {@code src/test/resources/benchmark/scenarios},
 * with resumes and job descriptions in sibling directories. A directory rather
 * than one large JSON document, so a scenario is reviewable on its own and a diff
 * shows which scenario changed.
 *
 * <p>Resumes and job descriptions are shared between scenarios on purpose. Two
 * scenarios differing only in which job description they use is a much sharper
 * test than two scenarios that also differ in the resume, and it keeps the
 * corpus small enough that someone actually reads it.
 */
public final class BenchmarkCorpus {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private BenchmarkCorpus() {}

    /** One scenario's inputs and its hand-written expectations. */
    public record Scenario(
            String id,
            String group,
            String description,
            String resume,
            String jobDescription,
            Expectations expectations
    ) {}

    /**
     * The expected behaviour of one scenario.
     *
     * <p>Every field is optional. A scenario asserts what it is actually trying to
     * prove, and a scenario that only cares about the overall range does not have
     * to invent an expectation for every category - a benchmark full of
     * unfalsifiable assertions is worse than a small one that can fail.
     *
     * <p>No exact score is ever expected. Ranges, ratios and states are what a
     * human can reason about, and they survive a legitimate change to the scoring
     * constants in a way that a pinned total does not.
     */
    public record Expectations(
            Integer[] overallRange,
            Integer[] requiredSkillsRange,
            Double minRequiredMatchedRatio,
            Integer[] experienceRange,
            String expectedExperienceStatus,
            Double minResponsibilitiesMatchedRatio,
            Double minPreferredMatchedRatio,
            /** Requirement name (exact) to expected match state. */
            java.util.Map<String, String> expectStatus,
            /** Requirement names (exact) that must NOT be matched. */
            List<String> expectNotExplicit,
            /** Requirement names (exact) expected to be only partially evidenced. */
            List<String> expectPartial,
            /** Canonical term that must never appear as satisfied. Negative synonym tests. */
            List<String> forbidSatisfiedTerms,
            /** The reasoning behind the range, recorded so it can be reviewed. */
            String rationale
    ) {}

    public static List<Scenario> load() {
        return load(Path.of("src/test/resources/benchmark"));
    }

    public static List<Scenario> load(Path root) {
        List<Scenario> scenarios = new ArrayList<>();
        Path scenarioDir = root.resolve("scenarios");
        if (!Files.isDirectory(scenarioDir)) {
            throw new IllegalStateException("Benchmark scenario directory not found: " + scenarioDir.toAbsolutePath());
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(scenarioDir, "*.json")) {
            for (Path file : files) {
                scenarios.add(parse(file, root));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the benchmark corpus", e);
        }
        scenarios.sort((a, b) -> a.id().compareTo(b.id()));
        return scenarios;
    }

    private static Scenario parse(Path scenarioFile, Path root) {
        try (InputStream in = Files.newInputStream(scenarioFile)) {
            JsonNode node = MAPPER.readTree(in);
            return new Scenario(
                    text(node, "id"),
                    text(node, "group"),
                    text(node, "description"),
                    readResource(root.resolve("resumes").resolve(text(node, "resume"))),
                    readResource(root.resolve("jds").resolve(text(node, "jd"))),
                    expectations(node.get("expectations")));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read benchmark scenario " + scenarioFile, e);
        }
    }

    private static Expectations expectations(JsonNode node) {
        if (node == null || node.isNull()) {
            return new Expectations(null, null, null, null, null, null, null, null, null, null, null, null);
        }
        return new Expectations(
                intRange(node.get("overallRange")),
                intRange(node.get("requiredSkillsRange")),
                number(node.get("minRequiredMatchedRatio")),
                intRange(node.get("experienceRange")),
                text(node, "expectedExperienceStatus"),
                number(node.get("minResponsibilitiesMatchedRatio")),
                number(node.get("minPreferredMatchedRatio")),
                stringMap(node.get("expectStatus")),
                textList(node.get("expectNotExplicit")),
                textList(node.get("expectPartial")),
                textList(node.get("forbidSatisfiedTerms")),
                text(node, "rationale"));
    }

    private static Integer[] intRange(JsonNode node) {
        if (node == null || !node.isArray() || node.size() != 2) {
            return null;
        }
        return new Integer[]{node.get(0).asInt(), node.get(1).asInt()};
    }

    private static Double number(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private static java.util.Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }

    private static String readResource(Path path) {
        try {
            if (!Files.exists(path)) {
                throw new IllegalStateException("Benchmark fixture not found: " + path.toAbsolutePath());
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read benchmark fixture " + path, e);
        }
    }
}
