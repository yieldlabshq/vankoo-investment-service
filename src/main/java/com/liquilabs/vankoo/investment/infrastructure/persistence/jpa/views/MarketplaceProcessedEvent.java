package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views;

import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "marketplace_processed_events")
public class MarketplaceProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "sequence_no", nullable = false)
    private long sequence;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketplaceProcessedEventStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "deferred_reason", length = 500)
    private String deferredReason;

    @Version
    private long version;

    protected MarketplaceProcessedEvent() {
    }

    public MarketplaceProcessedEvent(ProjectAuctionLifecycleEventCommand command, Instant receivedAt) {
        this.eventId = command.eventId();
        this.aggregateId = command.aggregateId();
        this.sequence = command.sequence();
        this.eventType = command.eventType().name();
        this.schemaVersion = command.schemaVersion();
        this.occurredAt = command.occurredAt();
        this.payload = command.rawPayload();
        this.status = MarketplaceProcessedEventStatus.RECEIVED;
        this.receivedAt = receivedAt;
    }

    public void defer(String reason) {
        status = MarketplaceProcessedEventStatus.DEFERRED;
        deferredReason = reason == null ? null : reason.substring(0, Math.min(500, reason.length()));
    }

    public void markApplied(Instant now) {
        status = MarketplaceProcessedEventStatus.APPLIED;
        appliedAt = now;
        deferredReason = null;
    }

    public String getEventId() { return eventId; }
    public String getAggregateId() { return aggregateId; }
    public long getSequence() { return sequence; }
    public String getEventType() { return eventType; }
    public int getSchemaVersion() { return schemaVersion; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPayload() { return payload; }
    public MarketplaceProcessedEventStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getAppliedAt() { return appliedAt; }
    public String getDeferredReason() { return deferredReason; }
}
