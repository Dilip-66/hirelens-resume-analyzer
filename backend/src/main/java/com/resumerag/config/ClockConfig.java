package com.resumerag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock the analysis engine reads.
 *
 * <p>A bean rather than a static call, for one reason: an open-ended employment
 * entry ("2021 - Present") resolves against the current month, so anything that
 * scores a resume becomes a function of the day it ran. Injecting the clock lets
 * the benchmark pin a reference date and produce the same numbers every time,
 * which is the difference between a regression test and a snapshot.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock analysisClock() {
        return Clock.systemDefaultZone();
    }
}
