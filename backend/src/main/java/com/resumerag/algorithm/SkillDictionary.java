package com.resumerag.algorithm;

import java.util.List;

/**
 * The closed vocabulary the skill trie scans for.
 *
 * <p>Being a closed list is the point: it is what makes matched/missing skills
 * auditable and free of model hallucination. The cost is that anything absent
 * here is structurally invisible, so the list has to cover the roles the product
 * is actually used for. It previously skewed hard to backend engineering, which
 * made a UI/UX or data-analytics resume score as if it had no skills at all.
 *
 * <p>Entries are stored without a trailing plural because the trie matches a
 * single plural "s" for free (see SkillTrie#isPluralBoundary).
 *
 * <p>Two rules keep the list from double-counting or false-positiving:
 * <ul>
 *   <li>Never list both a term and a longer term that starts with it
 *       ("Tailwind" alongside "Tailwind CSS"). The trie would report both for
 *       one mention, inflating the match.</li>
 *   <li>List the SHORTER form where the longer one is a superset, because the
 *       trie matches the shorter term inside the longer phrase at the word
 *       boundary - "Spark" alone catches both "Spark" and "Apache Spark".</li>
 * </ul>
 *
 * <p>Deliberately excluded: single letters, because whole-word matching would
 * score the "R" in "R&amp;D" as the R language, and other tokens that collide
 * with ordinary prose.
 */
public final class SkillDictionary {
    private SkillDictionary() {}

    public static final List<String> SKILLS = List.of(
        // --- Languages ---
        "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Rust", "Ruby", "PHP",
        "Swift", "Kotlin", "Scala", "Perl", "Julia", "Elixir", "Haskell", "Dart",

        // --- Frontend ---
        "React", "Angular", "Vue.js", "Next.js", "Nuxt.js", "Redux", "Svelte",
        "HTML", "CSS", "SASS", "Tailwind CSS", "Bootstrap", "Webpack", "Vite",
        "Responsive Design", "Web Accessibility", "Web Performance",

        // --- Backend / API ---
        "Spring Boot", "Spring", "Hibernate", "JPA", "Django", "Flask", "FastAPI",
        "Express.js", "NestJS", "REST API", "GraphQL", "gRPC", "OpenAPI", "Swagger",
        "Microservices", "Event-Driven Architecture", "Domain-Driven Design",

        // --- Databases ---
        "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Cassandra",
        "DynamoDB", "Snowflake", "BigQuery", "Redshift", "SQL", "NoSQL",
        "Database Design", "Query Optimization",

        // --- Messaging / streaming ---
        "Kafka", "RabbitMQ", "Pulsar", "Event Streaming",

        // --- Cloud / infrastructure ---
        "AWS", "Azure", "Google Cloud", "Docker", "Kubernetes", "Terraform",
        "Ansible", "Pulumi", "CI/CD", "Jenkins", "GitHub Actions", "GitLab CI",
        "Nginx", "Linux", "Bash", "Shell Scripting", "Serverless",
        "Infrastructure as Code", "Observability", "OpenTelemetry", "Prometheus",
        "Grafana", "Site Reliability Engineering",

        // --- Data / analytics ---
        "Pandas", "NumPy", "Matplotlib", "Seaborn", "Tableau", "Power BI",
        "Power Query", "Excel", "Google Analytics", "ETL", "ELT",
        "Data Modeling", "Data Warehousing", "Data Visualization", "Data Analysis",
        "Statistics", "A/B Testing", "Spark", "Hadoop", "Airflow", "dbt",
        "Data Pipeline", "Big Data",

        // --- ML / AI ---
        "Machine Learning", "Deep Learning", "Natural Language Processing", "NLP",
        "TensorFlow", "PyTorch", "Keras", "Computer Vision", "LLM",

        // --- Design ---
        "Figma", "Photoshop", "Illustrator", "Sketch", "Canva",
        "UI/UX", "Wireframing", "Prototyping", "Design Systems", "Interaction Design",
        "Usability Testing", "User Research", "Visual Design", "Branding",
        "Digital Marketing", "Design Thinking", "Information Architecture",

        // --- Mobile ---
        "React Native", "Flutter", "Android", "iOS", "Mobile Development",

        // --- Practices / tooling ---
        "Git", "Agile", "Scrum", "Kanban", "JIRA", "Confluence", "Trello",
        "Unit Testing", "Integration Testing", "Test-Driven Development",
        "Continuous Integration", "Code Review", "Technical Documentation"
    );
}
