package com.taxfiling.observability;

import com.taxfiling.repository.TaxFilingRepository;
import com.taxfiling.repository.TaxRuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for TaxFiling application.
 * Checks critical dependencies and reports their status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxFilingHealthIndicator implements HealthIndicator {

    private final TaxFilingRepository taxFilingRepository;
    private final TaxRuleVersionRepository taxRuleVersionRepository;

    @Override
    public Health health() {
        try {
            // Check database connectivity
            long filingCount = taxFilingRepository.count();
            long activeRulesCount = taxRuleVersionRepository.countActiveRules();

            return Health.up()
                    .withDetail("database", "connected")
                    .withDetail("totalFilings", filingCount)
                    .withDetail("activeRules", activeRulesCount)
                    .withDetail("status", activeRulesCount > 0 ? "ready" : "no_active_rules")
                    .build();
        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down()
                    .withDetail("database", "disconnected")
                    .withException(e)
                    .build();
        }
    }
}
