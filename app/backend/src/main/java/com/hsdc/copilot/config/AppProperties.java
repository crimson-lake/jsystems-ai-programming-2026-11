package com.hsdc.copilot.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings: case idle TTL and the zone that defines "today" (PRD section 12). */
@ConfigurationProperties("app")
public record AppProperties(Duration caseTtl, ZoneId zone) {
}
