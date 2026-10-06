package com.smartmed;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmartMedApplicationTests {

    @Autowired private Clock applicationClock;

    @Test
    void contextLoads() {
    }

    @Test
    void applicationClockUsesConfiguredCalendarTimezone() {
        assertThat(applicationClock.getZone()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
