package com.liquilabs.vankoo.investment.domain.model.commands;

import java.time.Instant;
import java.util.Map;

public record ProjectAuctionLifecycleEventCommand(
        String eventId,
        AuctionLifecycleProjectionEventType eventType,
        int schemaVersion,
        String aggregateId,
        long sequence,
        Instant occurredAt,
        Map<String, Object> data,
        String rawPayload
) {
}
