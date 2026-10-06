package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "investment_outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "investment_outbox_generator")
    @SequenceGenerator(
            name = "investment_outbox_generator",
            sequenceName = "investment_outbox_sequence",
            allocationSize = 1
    )
    @Column(name = "sequence_no")
    private Long sequence;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "binding_name", nullable = false, length = 100)
    private String bindingName;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Version
    private long version;

    protected OutboxEvent() {
    }

    public OutboxEvent(
            String eventId,
            String aggregateId,
            String eventType,
            int schemaVersion,
            String bindingName,
            Instant now
    ) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.bindingName = bindingName;
        this.payload = "{}";
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    OutboxEvent(
            long sequence,
            String eventId,
            String aggregateId,
            String eventType,
            int schemaVersion,
            String bindingName,
            String payload,
            Instant now
    ) {
        this(eventId, aggregateId, eventType, schemaVersion, bindingName, now);
        this.sequence = sequence;
        this.payload = payload;
    }

    public void attachPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Outbox payload is required");
        }
        this.payload = payload;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void markFailed(String error, Instant now) {
        attemptCount++;
        long delaySeconds = Math.min(300L, 1L << Math.min(attemptCount, 8));
        nextAttemptAt = now.plusSeconds(delaySeconds);
        lastError = truncate(error == null ? "Unknown publication error" : error);
    }

    private static String truncate(String value) {
        return value.substring(0, Math.min(1000, value.length()));
    }

    public Long getSequence() { return sequence; }
    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getBindingName() { return bindingName; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
