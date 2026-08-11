package com.joaosousa.atlas.seed;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@TestConfiguration
public class FixedClockConfig {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 8, 2);

    @Bean
    @Primary
    public Clock fixedClock() {
        return Clock.fixed(REFERENCE_DATE.atStartOfDay(ZONE).toInstant(), ZONE);
    }
}
