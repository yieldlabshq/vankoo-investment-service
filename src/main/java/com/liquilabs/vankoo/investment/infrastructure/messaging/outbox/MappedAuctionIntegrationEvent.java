package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventData;

import java.time.Instant;

public record MappedAuctionIntegrationEvent(
        String aggregateId,
        String eventType,
        Instant occurredAt,
        AuctionLifecycleEventData data
) {
}
