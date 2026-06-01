package ai.madpolicy.diplomacy.app;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(@Value("${diplomacy.clock.fixed-epoch-seconds:}") String fixedEpochSeconds) {
        if (fixedEpochSeconds == null || fixedEpochSeconds.isBlank()) {
            return Clock.systemUTC();
        }
        return Clock.fixed(Instant.ofEpochSecond(Long.parseLong(fixedEpochSeconds)), ZoneOffset.UTC);
    }
}
