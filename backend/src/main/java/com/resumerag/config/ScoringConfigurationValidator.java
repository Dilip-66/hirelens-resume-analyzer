package com.resumerag.config;

import com.resumerag.analysis.scoring.ScoringConfiguration;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates the scoring configuration once, at startup.
 *
 * <p>Scores are the number a hiring decision rests on, and a misconfigured weight
 * would not throw anywhere - it would just produce plausible, wrong percentages.
 * Failing at startup makes the mistake a deployment error rather than something a
 * candidate absorbs.
 */
@Component
public class ScoringConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ScoringConfigurationValidator.class);

    private final ScoringConfiguration scoringConfiguration;

    public ScoringConfigurationValidator(ScoringConfiguration scoringConfiguration) {
        this.scoringConfiguration = scoringConfiguration;
    }

    @PostConstruct
    public void validate() {
        scoringConfiguration.validate();
    }
}
