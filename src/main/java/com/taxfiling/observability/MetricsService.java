package com.taxfiling.observability;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service for recording custom business metrics.
 * Uses Micrometer for metrics collection, compatible with Prometheus/Grafana.
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter filingsCreatedCounter;
    private final Counter filingsSubmittedCounter;
    private final Counter calculationsPerformedCounter;
    private final Counter authSuccessCounter;
    private final Counter authFailureCounter;

    // Timers
    private final Timer calculationTimer;
    private final Timer submissionTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.filingsCreatedCounter = Counter.builder("taxfiling.filings.created")
                .description("Total number of filings created")
                .register(meterRegistry);

        this.filingsSubmittedCounter = Counter.builder("taxfiling.filings.submitted")
                .description("Total number of filings submitted")
                .register(meterRegistry);

        this.calculationsPerformedCounter = Counter.builder("taxfiling.calculations.performed")
                .description("Total number of tax calculations performed")
                .register(meterRegistry);

        this.authSuccessCounter = Counter.builder("taxfiling.auth.success")
                .description("Total number of successful authentications")
                .register(meterRegistry);

        this.authFailureCounter = Counter.builder("taxfiling.auth.failure")
                .description("Total number of failed authentications")
                .register(meterRegistry);

        // Initialize timers
        this.calculationTimer = Timer.builder("taxfiling.calculation.duration")
                .description("Time taken to perform tax calculations")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.submissionTimer = Timer.builder("taxfiling.submission.duration")
                .description("Time taken to submit filings")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("MetricsService initialized with custom business metrics");
    }

    // ========== Counter Methods ==========

    public void recordFilingCreated(String jurisdiction, int taxYear) {
        filingsCreatedCounter.increment();
        Counter.builder("taxfiling.filings.created.detailed")
                .tag("jurisdiction", jurisdiction)
                .tag("taxYear", String.valueOf(taxYear))
                .register(meterRegistry)
                .increment();
    }

    public void recordFilingSubmitted(String jurisdiction, int taxYear, boolean isRefund) {
        filingsSubmittedCounter.increment();
        Counter.builder("taxfiling.filings.submitted.detailed")
                .tag("jurisdiction", jurisdiction)
                .tag("taxYear", String.valueOf(taxYear))
                .tag("result", isRefund ? "refund" : "owing")
                .register(meterRegistry)
                .increment();
    }

    public void recordCalculation(String jurisdiction, int taxYear) {
        calculationsPerformedCounter.increment();
        Counter.builder("taxfiling.calculations.detailed")
                .tag("jurisdiction", jurisdiction)
                .tag("taxYear", String.valueOf(taxYear))
                .register(meterRegistry)
                .increment();
    }

    public void recordAuthSuccess() {
        authSuccessCounter.increment();
    }

    public void recordAuthFailure(String reason) {
        authFailureCounter.increment();
        Counter.builder("taxfiling.auth.failure.detailed")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordStatusTransition(String fromStatus, String toStatus) {
        Counter.builder("taxfiling.status.transitions")
                .tag("from", fromStatus)
                .tag("to", toStatus)
                .register(meterRegistry)
                .increment();
    }

    // ========== Timer Methods ==========

    public void recordCalculationTime(long durationMs) {
        calculationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordSubmissionTime(long durationMs) {
        submissionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Time a calculation operation and record the duration
     */
    public <T> T timeCalculation(Supplier<T> operation) {
        return calculationTimer.record(operation);
    }

    /**
     * Time a submission operation and record the duration
     */
    public <T> T timeSubmission(Supplier<T> operation) {
        return submissionTimer.record(operation);
    }

    // ========== Gauge Methods ==========

    /**
     * Register a gauge that tracks a value from a supplier
     */
    public <T extends Number> void registerGauge(String name, String description, Supplier<T> valueSupplier) {
        Gauge.builder(name, (Supplier<Number>) valueSupplier)
                .description(description)
                .register(meterRegistry);
    }

    // ========== Error Recording ==========

    public void recordError(String operation, String errorType) {
        Counter.builder("taxfiling.errors")
                .tag("operation", operation)
                .tag("errorType", errorType)
                .register(meterRegistry)
                .increment();
    }

    public void recordApiError(int statusCode, String path) {
        Counter.builder("taxfiling.api.errors")
                .tag("statusCode", String.valueOf(statusCode))
                .tag("path", sanitizePath(path))
                .register(meterRegistry)
                .increment();
    }

    private String sanitizePath(String path) {
        // Replace UUIDs and IDs with placeholders for metric cardinality
        return path.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{id}")
                   .replaceAll("/\\d+", "/{id}");
    }
}
