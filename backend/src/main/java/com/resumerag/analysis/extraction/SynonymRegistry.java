package com.resumerag.analysis.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The closed vocabulary that turns a job description's wording into something
 * comparable.
 *
 * <p>Three jobs, one table. It decides which surface forms mean the same thing
 * ("REST API" / "REST APIs" / "RESTful APIs" / "RESTful services" are one
 * requirement, not four), supplies the weaker related terms that count as
 * contextual rather than explicit evidence, and supplies the phrasing that makes
 * a "basic familiarity" requirement different from an "advanced" one.
 *
 * <h2>Why this table is short and hand-checked</h2>
 *
 * <p>Every entry is a judgement that two people would agree about, and the
 * benchmark is what keeps that judgement honest. The failure mode this guards
 * against is not a missing synonym, which is merely a missed match; it is an
 * invented one, which is a false positive a user has no reason to question.
 *
 * <p>Three rules, each of which exists because the benchmark caught its absence:
 *
 * <ul>
 *   <li><b>Related is not equivalent.</b> AWS and Azure are different products, as
 *       are Java and JavaScript, React and React Native, PostgreSQL and MySQL,
 *       Spring and Spring Boot, machine learning and deep learning. Each is a
 *       separate term. Collapsing siblings is how a candidate ends up reported as
 *       using Azure because they used AWS.</li>
 *   <li><b>Longer names win.</b> "React Native" must not be satisfied by React.
 *       A surface form is only matched when no longer registered term contains it,
 *       which is what stops a substring scan from shredding a compound name into
 *       the shorter technology built inside it.</li>
 *   <li><b>Cues are phrases, not nouns.</b> The word "service" appearing in "service
 *       layer" is not evidence of microservices, so bare generic nouns are not
 *       cues. A cue has to be specific enough that a hit means the capability.</li>
 * </ul>
 */
public final class SynonymRegistry {

    private SynonymRegistry() {}

    /**
     * Canonical term -> the surface forms that mean the same thing.
     *
     * <p>Multi-word first in each list purely for readability; matching is
     * length-ordered at lookup time, not insertion-ordered.
     */
    private static final Map<String, List<String>> CANONICAL_TERMS = buildCanonicalTerms();

    private static final Map<String, String> DISPLAY_NAMES = Map.ofEntries(
            Map.entry("JAVA", "Java"),
            Map.entry("SPRING_BOOT", "Spring Boot"),
            Map.entry("SPRING", "Spring"),
            Map.entry("REACT", "React"),
            Map.entry("REACT_NATIVE", "React Native"),
            Map.entry("ANGULAR", "Angular"),
            Map.entry("VUE", "Vue.js"),
            Map.entry("JAVASCRIPT", "JavaScript"),
            Map.entry("TYPESCRIPT", "TypeScript"),
            Map.entry("NODE_JS", "Node.js"),
            Map.entry("REST_API", "REST APIs"),
            Map.entry("GRAPHQL", "GraphQL"),
            Map.entry("POSTGRESQL", "PostgreSQL"),
            Map.entry("MYSQL", "MySQL"),
            Map.entry("SQL", "SQL"),
            Map.entry("MONGODB", "MongoDB"),
            Map.entry("REDIS", "Redis"),
            Map.entry("KAFKA", "Kafka"),
            Map.entry("AWS", "AWS"),
            Map.entry("AZURE", "Azure"),
            Map.entry("GCP", "Google Cloud"),
            Map.entry("EC2", "AWS EC2"),
            Map.entry("S3", "AWS S3"),
            Map.entry("CLOUD_PLATFORM", "Cloud platforms"),
            Map.entry("DOCKER", "Docker"),
            Map.entry("KUBERNETES", "Kubernetes"),
            Map.entry("TERRAFORM", "Terraform"),
            Map.entry("ANSIBLE", "Ansible"),
            Map.entry("LINUX", "Linux"),
            Map.entry("BASH", "Bash"),
            Map.entry("GIT", "Git"),
            Map.entry("GITHUB", "GitHub"),
            Map.entry("CI_CD", "CI/CD"),
            Map.entry("GITHUB_ACTIONS", "GitHub Actions"),
            Map.entry("JENKINS", "Jenkins"),
            Map.entry("MICROSERVICES", "Microservices"),
            Map.entry("PYTHON", "Python"),
            Map.entry("SCALA", "Scala"),
            Map.entry("KOTLIN", "Kotlin"),
            Map.entry("C_SHARP", "C#"),
            Map.entry("GO_LANG", "Go"),
            Map.entry("RUBY", "Ruby"),
            Map.entry("PHP", "PHP"),
            Map.entry("AUTOMATED_TESTING", "Automated testing"),
            Map.entry("JUNIT", "JUnit"),
            Map.entry("MACHINE_LEARNING", "Machine learning"),
            Map.entry("DEEP_LEARNING", "Deep learning"),
            Map.entry("ARTIFICIAL_INTELLIGENCE", "Artificial intelligence"),
            Map.entry("PYTORCH", "PyTorch"),
            Map.entry("TENSORFLOW", "TensorFlow"),
            Map.entry("PANDAS", "pandas"),
            Map.entry("TABLEAU", "Tableau"),
            Map.entry("POWER_BI", "Power BI"),
            Map.entry("DBT", "dbt"),
            Map.entry("AIRFLOW", "Airflow"),
            Map.entry("SPARK", "Apache Spark"),
            Map.entry("DATA_ANALYSIS", "Data analysis"),
            Map.entry("DATA_VISUALIZATION", "Data visualization"),
            Map.entry("EXCEL", "Excel"),
            Map.entry("WEB_ACCESSIBILITY", "Web accessibility"),
            Map.entry("RESPONSIVE_DESIGN", "Responsive interfaces"),
            Map.entry("WEB_PERFORMANCE", "Web performance"),
            Map.entry("SEO", "SEO"),
            Map.entry("GOOGLE_ANALYTICS", "Google Analytics"),
            Map.entry("A_B_TESTING", "A/B testing"),
            Map.entry("CONTENT_STRATEGY", "Content strategy"),
            Map.entry("SEO_COPYWRITING", "Copywriting"),
            Map.entry("HUBSPOT", "HubSpot"),
            Map.entry("INFRASTRUCTURE_AS_CODE", "Infrastructure as code"),
            Map.entry("OBSERVABILITY", "Observability"),
            Map.entry("PROMETHEUS", "Prometheus"),
            Map.entry("GRAFANA", "Grafana"),
            Map.entry("SDLC", "Software development lifecycle"),
            Map.entry("CODE_REVIEW", "Code reviews"),
            Map.entry("CLEAN_CODE", "Clean, maintainable code"),
            Map.entry("PROBLEM_SOLVING", "Problem solving"),
            Map.entry("DEBUGGING", "Debugging"),
            Map.entry("QUERY_OPTIMIZATION", "Query optimization"),
            Map.entry("PERFORMANCE", "Application performance"),
            Map.entry("API_DESIGN", "API design"),
            Map.entry("COLLABORATION", "Collaboration"),
            Map.entry("COMMUNICATION", "Communication"),
            Map.entry("FRONTEND", "Frontend development"),
            Map.entry("BACKEND", "Backend development"),
            Map.entry("DEPLOYMENT", "Deployment")
    );

    /**
     * Canonical term -> weaker associated wording, counted as contextual evidence.
     *
     * <p>Every entry is a phrase or an unambiguous technical term. The benchmark
     * removed a long tail of bare nouns from this map for a concrete reason: with
     * "service" as a microservices cue, a candidate whose resume mentioned a
     * "service layer" was credited with microservices experience on a
     * <em>required</em> skill. A cue that fires on ordinary prose is not evidence
     * of a capability, and a false match on a required skill is the most
     * expensive kind.
     */
    private static final Map<String, List<String>> CONTEXTUAL_CUES = buildContextualCues();

    private static Map<String, List<String>> buildContextualCues() {
        Map<String, List<String>> cues = new LinkedHashMap<>();
        cues.put("REST_API", List.of("endpoint", "endpoints", "api contract", "openapi", "swagger"));
        cues.put("API_DESIGN", List.of("openapi", "swagger", "api contract", "payload", "versioned endpoint"));
        cues.put("POSTGRESQL", List.of("rdbms", "relational database", "database schema"));
        cues.put("MYSQL", List.of("rdbms", "relational database"));
        cues.put("SQL", List.of("relational database", "database schema", "query", "queries"));
        cues.put("QUERY_OPTIMIZATION", List.of("optimized queries", "optimise queries", "query performance", "sql tuning", "execution plan", "index tuning"));
        cues.put("DOCKER", List.of("containerization", "containerisation", "containerized", "containerised", "dockerfile", "docker-compose", "containers"));
        cues.put("KUBERNETES", List.of("helm chart", "kubectl", "container orchestration", "namespace"));
        cues.put("AWS", List.of("ec2", "s3", "lambda", "rds", "cloudwatch", "vpc", "elastic beanstalk"));
        cues.put("CLOUD_PLATFORM", List.of("deploy", "deployed", "deployment", "hosting", "hosted", "infrastructure", "production environment", "cloud native"));
        cues.put("DEPLOYMENT", List.of("deploy", "deployed", "deployment", "release", "rollout", "hosting"));
        cues.put("REDIS", List.of("cache layer", "in-memory store"));
        cues.put("CACHING", List.of("response time", "latency", "memoization", "cache layer"));
        cues.put("MICROSERVICES", List.of("service boundary", "service boundaries", "api gateway", "independent deployment", "decomposed", "event-driven architecture"));
        cues.put("CI_CD", List.of("build pipeline", "deployment pipeline", "release pipeline", "automated release", "continuous delivery"));
        cues.put("GITHUB_ACTIONS", List.of("workflow file", "workflow files", "yaml workflow"));
        cues.put("AUTOMATED_TESTING", List.of("test coverage", "unit tests", "integration tests", "regression suite", "junit", "pytest", "test suites"));
        cues.put("JUNIT", List.of("junit 5", "junit5", "test coverage"));
        cues.put("SDLC", List.of("continuous integration", "continuous delivery", "version control", "code review", "agile", "sprint", "release process", "testing workflow", "testing workflows", "production release"));
        cues.put("CODE_REVIEW", List.of("pull request review", "pr review", "pair programming", "mentoring", "reviewing pull requests"));
        cues.put("CLEAN_CODE", List.of("reusable components", "modular design", "best practices", "design patterns", "readable code", "refactoring", "documented code"));
        cues.put("PROBLEM_SOLVING", List.of("optimization", "optimisation", "analytical thinking", "root cause", "trade-off", "tradeoff", "diagnosed", "resolved"));
        cues.put("DEBUGGING", List.of("bug fix", "bug fixes", "error handling", "exception handling", "incident", "outage", "root cause", "troubleshoot", "production issue", "production issues", "broken", "regression"));
        cues.put("PERFORMANCE", List.of("response time", "latency", "throughput", "load time", "bottleneck", "profiling", "faster", "reduced by", "improved by"));
        cues.put("WEB_PERFORMANCE", List.of("page load time", "lighthouse", "bundle size", "core web vitals", "lazy loading", "code splitting", "first paint"));
        cues.put("RESPONSIVE_DESIGN", List.of("mobile layout", "breakpoints", "reusable ui components", "responsive layout"));
        cues.put("WEB_ACCESSIBILITY", List.of("wcag", "wcag aa", "wcag 2", "a11y", "aria", "screen reader", "keyboard navigation", "accessible"));
        cues.put("FRONTEND", List.of("component library", "design system", "user interface", "ui components"));
        cues.put("BACKEND", List.of("server side", "service layer", "api endpoint"));
        cues.put("COLLABORATION", List.of("worked with", "work with", "partnered with", "stakeholders", "cross-functional", "cross functional", "team of", "COMMUNICATION", "communicated", "documented", "presented", "explained", "technical documentation", "wrote documentation"));
        cues.put("GIT", List.of("version control", "commit history", "branches", "merging"));
        cues.put("GITHUB", List.of("pull requests", "repository", "repositories", "source control"));
        cues.put("MACHINE_LEARNING", List.of("model training", "feature engineering", "supervised learning", "model evaluation", "recommendation model"));
        cues.put("DEEP_LEARNING", List.of("neural network", "neural networks", "convolutional", "transformer model", "model training"));
        cues.put("ARTIFICIAL_INTELLIGENCE", List.of("llm", "large language model", "ai assistant", "ai model", "ai models"));
        cues.put("PYTORCH", List.of("torch", "pytorch lightning"));
        cues.put("DATA_ANALYSIS", List.of("data analysis", "exploratory analysis", "data modelling", "data modeling", "insight", "insights"));
        cues.put("DATA_VISUALIZATION", List.of("dashboards", "dashboard", "charts", "reporting"));
        cues.put("SEO", List.of("search engine optimization", "search engine optimisation", "organic traffic", "keyword research", "serp"));
        cues.put("GOOGLE_ANALYTICS", List.of("ga4", "web analytics", "traffic analysis"));
        cues.put("A_B_TESTING", List.of("experiment", "experiments", "split test", "split testing", "a b test", "variant testing"));
        cues.put("CONTENT_STRATEGY", List.of("editorial calendar", "content plan", "content programme", "content program", "thought leadership"));
        return Map.copyOf(cues);
    }

    /**
     * Phrasing that means "one of these is enough" rather than a demonstrated gap.
     */
    private static final List<String> NEGATION_CUES = List.of(
            "no experience", "no knowledge", "no exposure", "no familiarity", "no professional",
            "not familiar", "not experienced", "not yet", "no hands-on", "no prior",
            "limited experience", "limited exposure", "minimal experience", "little experience",
            "never used", "have not used", "haven't used", "have not worked", "haven't worked",
            "currently learning", "learning ", "beginner", "novice", "basic familiarity"
    );

    /**
     * Past-tense achievement and quantified results.
     *
     * <p>These separate a demonstrated outcome from a bare contextual overlap.
     * "Reduced API response time by 30% through caching and query optimization"
     * is not the same kind of statement as "worked on caching", and an engine
     * that cannot tell them apart has to either overclaim the second or
     * understate the first.
     */
    private static final List<String> OUTCOME_MARKERS = List.of(
            "reduced", "improved", "increased", "decreased", "cut", "halved", "doubled",
            "saved", "grew", "accelerated", "eliminated", "trimmed", "scaled", "delivered",
            "achieved", "migrated", "designed", "implemented", "developed", "deployed",
            "automated", "optimized", "optimised", "refactored", "launched", "built",
            "created", "established", "introduced", "containerized", "containerised",
            "dockerized", "dockerised", "architected", "streamlined", "upgraded", "integrated",
            "configured", "orchestrated", "resolved", "diagnosed", "owned", "led", "shipped",
            "%", "x faster", "ms", "seconds", "seconds faster", "reduced by", "improved by"
    );

    /**
     * Category terms: a *kind* of work, satisfied by the technologies that are
     * instances of it.
     *
     * <p>Strictly categories. Siblings are excluded on purpose: AWS and Azure are
     * both cloud providers, but neither is a kind of the other, and letting one
     * satisfy a requirement written for the other is a false positive the user has
     * no way to catch. `CLOUD_PLATFORM` therefore sits above the named providers
     * as an *alternative* satisfaction for open wording ("cloud platforms"), not
     * as a synonym for any of them.
     */
    private static final Set<String> GENERIC_KEYS = Set.of(
            "FRONTEND", "BACKEND", "CLOUD_PLATFORM", "VERSION_CONTROL",
            "PERFORMANCE", "COMMUNICATION", "DEPLOYMENT", "CACHING");

    /** Named technologies that satisfy a category term. */
    private static final Map<String, List<String>> CATEGORY_MEMBERS = Map.of(
            "FRONTEND", List.of("REACT", "REACT_NATIVE", "ANGULAR", "VUE", "JAVASCRIPT", "TYPESCRIPT", "NODE_JS"),
            "BACKEND", List.of("JAVA", "SPRING_BOOT", "NODE_JS", "PYTHON", "SCALA", "KOTLIN", "GO_LANG", "RUBY", "PHP"),
            "CLOUD_PLATFORM", List.of("AWS", "AZURE", "GCP"),
            "VERSION_CONTROL", List.of("GIT", "GITHUB"),
            "COMMUNICATION", List.of("COLLABORATION"),
            "DEPLOYMENT", List.of("CI_CD", "DOCKER", "KUBERNETES", "CLOUD_PLATFORM", "AWS", "AZURE", "GCP"));

    /** Normalised surface form -> canonical key, for exact lookup. */
    private static final Map<String, String> EXACT_INDEX = buildExactIndex();

    /** Every surface form, longest first, for longest-match scanning. */
    private static final List<Map.Entry<String, String>> PATTERNS_BY_LENGTH = buildPatternsByLength();

    /**
     * Surface forms that must never be reduced to a shorter term they contain.
     *
     * <p>The guard for "React Native" and "Deep Learning". Held explicitly rather
     * than derived, because deriving it correctly means a full longest-match parse
     * over a vocabulary that will keep growing - and a wrong derivation here
     * produces false matches on required skills, which is the most expensive
     * failure this component can have. Each entry is a real product name that
     * contains a shorter registered technology.
     */
    private static final Map<String, String> COMPOUND_GUARDS = Map.ofEntries(
            Map.entry("react native", "REACT_NATIVE"),
            Map.entry("next js", "REACT"),
            Map.entry("nextjs", "REACT"),
            Map.entry("spring cloud", "SPRING_BOOT"),
            Map.entry("deep learning", "DEEP_LEARNING"),
            Map.entry("machine learning", "MACHINE_LEARNING"),
            Map.entry("artificial intelligence", "ARTIFICIAL_INTELLIGENCE"),
            Map.entry("design patterns", "CLEAN_CODE"),
            Map.entry("unit testing", "AUTOMATED_TESTING"),
            Map.entry("test driven development", "AUTOMATED_TESTING"),
            Map.entry("amazon web services", "AWS"),
            Map.entry("google cloud", "GCP"),
            Map.entry("version control", "VERSION_CONTROL"),
            Map.entry("amazon ec2", "EC2"),
            Map.entry("amazon s3", "S3"),
            Map.entry("node js", "NODE_JS"),
            Map.entry("apache spark", "SPARK"),
            Map.entry("web accessibility", "WEB_ACCESSIBILITY"),
            Map.entry("web performance", "WEB_PERFORMANCE"),
            Map.entry("responsive design", "RESPONSIVE_DESIGN"),
            Map.entry("problem solving", "PROBLEM_SOLVING"),
            Map.entry("api design", "API_DESIGN"),
            Map.entry("infrastructure as code", "INFRASTRUCTURE_AS_CODE"),
            Map.entry("a b testing", "A_B_TESTING"),
            Map.entry("content strategy", "CONTENT_STRATEGY"),
            Map.entry("data analysis", "DATA_ANALYSIS"),
            Map.entry("data visualization", "DATA_VISUALIZATION"));

    private static final Map<String, Pattern> WHOLE_WORD_CACHE = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * The canonical entry for a phrase, or empty when the phrase is not in the
     * vocabulary.
     *
     * <p>Returning empty is the normal case for a real job description - plenty
     * of requirements are genuine phrases rather than named technologies, and the
     * normalizer handles those by matching the phrase itself. Only a hit here
     * grants cross-wording equivalence.
     */
    public static Optional<NormalizedEntry> lookup(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }
        String key = resolve(phrase);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.of(new NormalizedEntry(key, displayName(key), CANONICAL_TERMS.get(key)));
    }

    /**
     * The canonical key for a term named inside {@code phrase}, or empty.
     *
     * <p>Consults {@link #COMPOUND_GUARDS} first, which is what keeps a longer
     * product name from being reduced to the shorter technology embedded in it.
     */
    private static String resolve(String phrase) {
        String normalized = normalizePhrase(phrase);
        if (normalized.isBlank()) {
            return null;
        }
        String guarded = longestGuardIn(normalized);
        if (guarded != null) {
            return guarded;
        }
        return EXACT_INDEX.get(normalized);
    }

    /**
     * The longest compound guard appearing as a whole phrase, or {@code null}.
     */
    private static String longestGuardIn(String normalizedText) {
        String padded = " " + normalizedText + " ";
        String bestKey = null;
        int bestLength = 0;
        for (Map.Entry<String, String> guard : COMPOUND_GUARDS.entrySet()) {
            if (guard.getKey().length() <= bestLength) {
                continue;
            }
            if (padded.contains(" " + guard.getKey() + " ")) {
                bestKey = guard.getValue();
                bestLength = guard.getKey().length();
            }
        }
        return bestKey;
    }

    /**
     * Every technology a piece of resume text names.
     *
     * <p>Deliberately inclusive. A resume line reading "GitHub Actions" really
     * does demonstrate GitHub, and one reading "deployed to AWS EC2" really does
     * demonstrate AWS, so both keys are returned and either requirement can be
     * evidenced by that line. The requirement side is stricter - see
     * {@link #canonicalKeysForRequirement} - because a job description naming
     * "AWS EC2 / S3" is not also asking for bare AWS.
     */
    public static List<String> canonicalKeysIn(String phrase) {
        List<Match> matches = matchesIn(phrase);
        if (matches.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Match match : matches) {
            if (!keys.contains(match.key())) {
                keys.add(match.key());
            }
        }
        return keys;
    }

    /**
     * The canonical terms a job description's phrase asks for.
     *
     * <p>Reads the phrase left to right, taking the longest term at each position
     * and stepping past it. This is the ordinary tokenizer rule, and getting it
     * right is what the benchmark forced. It gives "AWS EC2" one reading, EC2, so a
     * candidate who has only written "AWS" in a skills list cannot satisfy a role
     * that named two specific services. It gives "RESTful API development" one
     * reading too - REST APIs - rather than splitting it into REST APIs plus API
     * design, because the REST term starts first and is taken whole. And it never
     * reads "TypeScript" as Java, since "typescript" is matched at position zero
     * and the scanner never looks inside a token it has already consumed.
     *
     * <p>The evidence side deliberately does not work this way. A resume line that
     * says "PostgreSQL and SQL" is evidence of both, so it is matched with
     * {@link #matchesIn}, which reports every hit.
     */
    public static List<String> canonicalKeysForRequirement(String phrase) {
        List<Match> matches = matchesIn(phrase);
        if (matches.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Match match : scanLeftToRight(matches)) {
            if (!keys.contains(match.key())) {
                keys.add(match.key());
            }
        }
        return keys;
    }

    /**
     * Non-overlapping matches, longest-first at each position, reading left to
     * right. A token that has been consumed is never re-read from the inside, which
     * is what keeps "typescript" from also registering as "java".
     */
    private static List<Match> scanLeftToRight(List<Match> matches) {
        List<Match> byPosition = new ArrayList<>(matches);
        byPosition.sort(Comparator
                .comparingInt(Match::start)
                .thenComparing(Comparator.comparingInt((Match m) -> m.end() - m.start()).reversed())
                .thenComparing(Match::key));

        List<Match> chosen = new ArrayList<>();
        int cursor = 0;
        int index = 0;
        while (index < byPosition.size()) {
            Match candidate = byPosition.get(index);
            if (candidate.start() < cursor) {
                index++;
                continue;
            }
            // Candidate is the earliest start still ahead of the cursor, and among
            // those the longest. Take it and step past everything it covers.
            chosen.add(candidate);
            cursor = candidate.end();
            while (index < byPosition.size() && byPosition.get(index).start() < cursor) {
                index++;
            }
        }
        return chosen;
    }

    /**
     * Every surface-form hit in a phrase, with the span it covers.
     *
     * <p>Spans rather than the matched words, and that is a correction the
     * benchmark forced. An earlier version compared matched <em>text</em> and
     * dropped any term whose letters appeared inside a longer match, which silently
     * removed "SQL" from a line saying "PostgreSQL ... SQL" - the letters really are
     * inside the word, but the two are separate facts and the line names both.
     */
    /** One surface-form hit: the span it covers and what it means. */
    private record Match(String surface, String key, int start, int end) {}

    private static List<Match> matchesIn(String phrase) {
        String normalized = normalizePhrase(phrase);
        if (normalized.isBlank()) {
            return List.of();
        }
        String guarded = longestGuardIn(normalized);
        if (guarded != null) {
            return List.of(new Match(normalized, guarded, 0, normalized.length()));
        }
        List<Match> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : PATTERNS_BY_LENGTH) {
            Matcher matcher = wholeWordPattern(entry.getKey()).matcher(normalized);
            while (matcher.find()) {
                matches.add(new Match(entry.getKey(), entry.getValue(),
                        matcher.start(), matcher.end()));
            }
        }
        matches.sort(Comparator.comparingInt(Match::start).thenComparing(Match::key));
        return matches;
    }

    /** One surface-form hit: where it was, how long it was, and what it means. */

    /**
     * Every registered surface form with its canonical key, longest first.
     *
     * <p>Ordered by length so a caller scanning for the longest match finds the
     * most specific term first - which is what stops "AWS EC2" being read as two
     * requirements, or "React Native" as React.
     */
    public static List<Map.Entry<String, String>> patternsByLength() {
        return PATTERNS_BY_LENGTH;
    }

    public static List<String> surfacesFor(String canonicalKey) {
        return CANONICAL_TERMS.getOrDefault(canonicalKey, List.of());
    }

    public static List<String> cuesFor(String canonicalKey) {
        return CONTEXTUAL_CUES.getOrDefault(canonicalKey, List.of());
    }

    public static String displayName(String canonicalKey) {
        return DISPLAY_NAMES.getOrDefault(canonicalKey, humanize(canonicalKey));
    }

    /** True for category terms such as FRONTEND or CLOUD_PLATFORM rather than named products. */
    public static boolean isGeneric(String canonicalKey) {
        return GENERIC_KEYS.contains(canonicalKey);
    }

    /** Named technologies that satisfy a category term. */
    public static List<String> membersOf(String canonicalKey) {
        return CATEGORY_MEMBERS.getOrDefault(canonicalKey, List.of());
    }

    /** True when the text reads as a denial or a stated goal rather than a capability. */
    public static boolean negates(String text) {
        String lower = lowercase(text);
        return NEGATION_CUES.stream().anyMatch(lower::contains);
    }

    /** True when the text shows a quantified or achieved result. */
    public static boolean isOutcomeBearing(String text) {
        String lower = lowercase(text);
        return OUTCOME_MARKERS.stream().anyMatch(lower::contains);
    }

    /**
     * Strips punctuation and case so "JavaScript/TypeScript" and
     * "javascript typescript" reduce to the same lookup key.
     */
    public static String normalizePhrase(String phrase) {
        if (phrase == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(phrase.length());
        for (char c : phrase.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Whole-word containment on normalised text, tolerating one plural "s".
     *
     * <p>The plural is the point. Skill names are written both ways far more
     * often than not - "REST API" in a job description, "Developed REST APIs" on a
     * resume - and without accepting it every pluralised technology reads as
     * absent, which is precisely the false negative this layer was built to
     * eliminate.
     *
     * <p>The alphanumeric lookarounds are load-bearing: they stop "sql" from
     * firing inside "nosql", "go" from firing inside "going", and "ai" from
     * firing inside "said". Short terms in a closed vocabulary with substring
     * matching produce confidently wrong matches.
     */
    public static boolean containsWholeWord(String normalizedText, String normalizedTerm) {
        if (normalizedText == null || normalizedTerm == null || normalizedTerm.isBlank()) {
            return false;
        }
        return wholeWordPattern(normalizedTerm).matcher(normalizedText).find();
    }

    /** Word-boundary pattern for a term, allowing a single trailing plural "s". */
    public static Pattern wholeWordPattern(String normalizedTerm) {
        return WHOLE_WORD_CACHE.computeIfAbsent(normalizedTerm,
                term -> Pattern.compile(
                        "(?<![a-z0-9])" + Pattern.quote(term) + "s?(?![a-z0-9])"));
    }

    public static String lowercase(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String humanize(String canonicalKey) {
        String[] parts = canonicalKey.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    private static Map<String, List<String>> buildCanonicalTerms() {
        Map<String, List<String>> m = new LinkedHashMap<>();

        // --- Languages. Each is its own term: Java is not JavaScript, Python is
        // not PyTorch, and a candidate offering one has not offered the other.
        m.put("JAVA", List.of("java", "java programming", "java language", "java development", "core java"));
        m.put("JAVASCRIPT", List.of("javascript", "java script", "js", "es6", "ecmascript"));
        m.put("TYPESCRIPT", List.of("typescript", "type script", "ts"));
        m.put("PYTHON", List.of("python", "python3", "python 3"));
        m.put("SCALA", List.of("scala"));
        m.put("KOTLIN", List.of("kotlin"));
        m.put("C_SHARP", List.of("c#", "c sharp", "csharp"));
        m.put("GO_LANG", List.of("golang"));
        m.put("RUBY", List.of("ruby", "ruby on rails", "rails"));
        m.put("PHP", List.of("php", "laravel", "symfony"));

        // --- Frontend. React and React Native are separate products, so a React
        // requirement is never satisfied by React Native alone and vice versa.
        m.put("REACT", List.of("react", "react.js", "reactjs", "react js"));
        m.put("REACT_NATIVE", List.of("react native", "react-native", "reactnative", "expo"));
        m.put("ANGULAR", List.of("angular", "angularjs", "angular.js"));
        m.put("VUE", List.of("vue", "vue.js", "vuejs"));
        m.put("NODE_JS", List.of("node.js", "nodejs", "node js", "node"));
        m.put("WEB_ACCESSIBILITY", List.of("web accessibility", "accessibility", "wcag", "a11y",
                "accessible interfaces"));
        m.put("RESPONSIVE_DESIGN", List.of("responsive design", "responsive", "responsive web",
                "responsive interfaces", "responsive web interfaces", "responsive ui"));
        m.put("WEB_PERFORMANCE", List.of("web performance", "web performance optimization",
                "website performance", "front-end performance"));

        // --- Backend / API
        m.put("SPRING_BOOT", List.of("spring boot", "springboot", "spring-boot"));
        m.put("SPRING", List.of("spring framework", "spring mvc", "spring web"));
        m.put("REST_API", List.of("rest api", "rest apis", "restful api", "restful apis",
                "restful services", "restful web services", "restful web service", "restful endpoint",
                "restful endpoints", "restful", "rest-based api", "rest based apis", "rest web services",
                "rest endpoint", "rest endpoints"));
        m.put("GRAPHQL", List.of("graphql", "graph ql"));
        m.put("MICROSERVICES", List.of("microservices", "microservice", "microservice architecture",
                "microservices architecture", "service oriented architecture", "service-oriented architecture"));
        m.put("API_DESIGN", List.of("api design", "designing apis", "design and integrate restful apis",
                "design restful apis", "designing rest apis", "api development", "rest api development",
                "design and build apis", "build and integrate apis"));

        // --- Databases. PostgreSQL and MySQL are different products and are kept
        // apart: a candidate who has only ever used PostgreSQL has not used MySQL.
        m.put("POSTGRESQL", List.of("postgresql", "postgres", "postgre sql", "psql", "pgsql"));
        m.put("MYSQL", List.of("mysql", "my sql", "mariadb"));
        m.put("MONGODB", List.of("mongodb", "mongo db", "mongo"));
        m.put("SQL", List.of("sql", "structured query language", "relational queries"));
        m.put("REDIS", List.of("redis", "redis cache", "redis cluster", "redis caching"));
        m.put("CACHING", List.of("caching", "cache", "caching strategy", "in-memory caching",
                "cache layer", "response caching"));
        m.put("KAFKA", List.of("kafka", "apache kafka", "event streaming"));

        // --- Cloud. Named providers are separate terms. The generic
        // CLOUD_PLATFORM below is for open wording only, and a named provider
        // satisfies it - but not the reverse.
        m.put("AWS", List.of("aws", "amazon web services", "amazon aws", "aws cloud"));
        m.put("AZURE", List.of("azure", "microsoft azure", "azure cloud"));
        m.put("GCP", List.of("google cloud", "google cloud platform", "gcp", "google cloud console"));
        m.put("EC2", List.of("ec2", "ec2 instances", "amazon ec2", "aws ec2"));
        m.put("S3", List.of("s3", "s3 bucket", "s3 buckets", "amazon s3", "aws s3"));
        m.put("CLOUD_PLATFORM", List.of("cloud platforms", "cloud platform", "cloud services",
                "cloud service", "cloud computing", "cloud provider", "cloud providers"));
        m.put("DOCKER", List.of("docker", "dockerized", "dockerised", "dockerize", "dockerise",
                "dockerization", "docker containerization", "containerization", "containerisation"));
        m.put("KUBERNETES", List.of("kubernetes", "k8s", "kube", "container orchestration"));
        m.put("TERRAFORM", List.of("terraform", "terraform modules", "hashicorp terraform"));
        m.put("ANSIBLE", List.of("ansible", "ansible playbook", "ansible playbooks"));
        m.put("LINUX", List.of("linux", "unix", "ubuntu", "debian"));
        m.put("BASH", List.of("bash", "shell scripting", "shell script", "bash scripting"));
        m.put("INFRASTRUCTURE_AS_CODE", List.of("infrastructure as code", "iac", "infrastructure automation"));
        m.put("OBSERVABILITY", List.of("observability", "monitoring and alerting", "slo", "sli"));
        m.put("PROMETHEUS", List.of("prometheus"));
        m.put("GRAFANA", List.of("grafana"));

        // --- Version control / CI
        m.put("GIT", List.of("git", "git version control"));
        m.put("GITHUB", List.of("github", "git hub", "github repositories", "github repo"));
        m.put("VERSION_CONTROL", List.of("version control", "source control", "revision control"));
        m.put("CI_CD", List.of("ci/cd", "cicd", "ci-cd", "continuous integration", "continuous delivery",
                "continuous deployment", "continuous integration and continuous delivery",
                "continuous integration and continuous deployment", "ci cd pipelines", "cd pipelines",
                "build pipeline", "deployment pipeline", "ci cd"));
        m.put("GITHUB_ACTIONS", List.of("github actions", "github action", "gh actions", "gh-action"));
        m.put("JENKINS", List.of("jenkins", "jenkins pipeline", "jenkins pipelines"));

        // --- Data / ML. Machine learning, deep learning and artificial
        // intelligence are three terms. They are related, not interchangeable, and
        // the task's own rules list them separately.
        m.put("MACHINE_LEARNING", List.of("machine learning", "ml", "supervised learning",
                "unsupervised learning"));
        m.put("DEEP_LEARNING", List.of("deep learning", "neural networks", "neural network"));
        m.put("ARTIFICIAL_INTELLIGENCE", List.of("artificial intelligence", "ai", "ai ml", "ai-ml",
                "genai", "generative ai"));
        m.put("PYTORCH", List.of("pytorch", "torch", "pytorch lightning"));
        m.put("TENSORFLOW", List.of("tensorflow", "keras"));
        m.put("PANDAS", List.of("pandas"));
        m.put("TABLEAU", List.of("tableau"));
        m.put("POWER_BI", List.of("power bi", "powerbi"));
        m.put("DBT", List.of("dbt"));
        m.put("AIRFLOW", List.of("airflow", "apache airflow", "dag orchestration"));
        m.put("SPARK", List.of("apache spark", "spark", "pyspark", "spark streaming"));
        m.put("DATA_ANALYSIS", List.of("data analysis", "data analytics", "business analysis",
                "exploratory data analysis"));
        m.put("DATA_VISUALIZATION", List.of("data visualization", "data visualisation",
                "data presentation", "visual analytics"));
        m.put("EXCEL", List.of("excel", "microsoft excel", "advanced excel", "pivot tables"));

        // --- Testing
        m.put("AUTOMATED_TESTING", List.of("automated testing", "automated tests", "automated test",
                "unit testing", "unit tests", "integration testing", "integration tests",
                "test coverage", "test-driven development", "test driven development", "tdd",
                "testing workflows", "testing workflow", "qa automation", "vitest", "jest"));
        m.put("JUNIT", List.of("junit", "junit 5", "junit5", "junit4"));

        // --- Marketing / content
        m.put("SEO", List.of("seo", "search engine optimization", "search engine optimisation"));
        m.put("GOOGLE_ANALYTICS", List.of("google analytics", "ga4", "universal analytics"));
        m.put("A_B_TESTING", List.of("a/b testing", "a b testing", "ab testing", "split testing",
                "split test", "a/b tests"));
        m.put("CONTENT_STRATEGY", List.of("content strategy", "content marketing strategy",
                "editorial strategy"));
        m.put("SEO_COPYWRITING", List.of("copywriting", "copy writing", "content writing", "blog writing"));
        m.put("HUBSPOT", List.of("hubspot"));

        // --- Ways of working
        m.put("SDLC", List.of("software development lifecycle", "sdlc", "development lifecycle",
                "software lifecycle", "product development lifecycle", "software engineering practices"));
        m.put("CODE_REVIEW", List.of("code review", "code reviews", "code-review", "peer review",
                "peer reviews", "reviewing code", "pull request review", "pr reviews", "code review process"));
        m.put("CLEAN_CODE", List.of("clean code", "clean, maintainable and scalable code",
                "clean maintainable and scalable code", "clean maintainable",
                "maintainable code", "scalable code", "code quality", "code maintainability",
                "readable code"));
        m.put("PROBLEM_SOLVING", List.of("problem solving", "problem-solving", "problem solving skills",
                "analytical thinking", "troubleshooting skills"));
        m.put("DEBUGGING", List.of("debugging", "debug", "debugged", "debugs", "debugging skills",
                "troubleshooting", "troubleshoot", "troubleshot", "bug fixing", "error handling"));
        m.put("QUERY_OPTIMIZATION", List.of("query optimization", "query optimisation",
                "optimize queries", "optimising queries", "optimized queries", "optimised queries",
                "optimize sql queries", "sql optimization", "query tuning", "index tuning",
                "optimize query performance", "optimised queries"));
        m.put("PERFORMANCE", List.of("application performance", "performance optimization",
                "performance optimisation", "performance tuning", "improve application performance",
                "api performance", "system performance", "performance improvement"));
        m.put("COLLABORATION", List.of("collaborate", "collaboration", "collaborating", "teamwork",
                "cross-functional collaboration", "work in a team", "team collaboration"));
        m.put("COMMUNICATION", List.of("communication", "communication skills", "communicating",
                "written communication", "verbal communication"));
        m.put("FRONTEND", List.of("frontend development", "front-end development", "frontend",
                "front-end", "frontend technologies"));
        m.put("BACKEND", List.of("backend development", "back-end development", "backend services",
                "backend", "back-end", "backend systems", "backend service"));
        m.put("DEPLOYMENT", List.of("deployment", "deployments", "deploying applications",
                "application deployment", "release management"));

        return Map.copyOf(m);
    }

    private static Map<String, String> buildExactIndex() {
        Map<String, String> index = new LinkedHashMap<>();
        CANONICAL_TERMS.forEach((key, surfaces) -> {
            index.putIfAbsent(normalizePhrase(key), key);
            for (String surface : surfaces) {
                index.putIfAbsent(normalizePhrase(surface), key);
            }
            index.putIfAbsent(normalizePhrase(displayName(key)), key);
        });
        return Map.copyOf(index);
    }

    private static List<Map.Entry<String, String>> buildPatternsByLength() {
        List<Map.Entry<String, String>> all = new java.util.ArrayList<>();
        CANONICAL_TERMS.forEach((key, surfaces) -> {
            for (String surface : surfaces) {
                String normalized = normalizePhrase(surface);
                if (!normalized.isBlank()) {
                    all.add(Map.entry(normalized, key));
                }
            }
        });
        all.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        return List.copyOf(all);
    }

    /** A vocabulary hit: the canonical key, its display name and its surface forms. */
    public record NormalizedEntry(String canonicalKey, String displayName, List<String> surfaces) {}
}
