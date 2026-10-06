package com.liquilabs.vankoo.investment.interfaces.events.resources;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record AuctionLifecycleIntegrationEvent<T extends AuctionLifecycleEventData>(
        String eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long sequence,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        T data
) {
}
