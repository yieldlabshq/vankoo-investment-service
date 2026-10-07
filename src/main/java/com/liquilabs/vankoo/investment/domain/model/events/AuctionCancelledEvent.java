package com.liquilabs.vankoo.investment.domain.model.events;

import java.time.Instant;
import java.util.List;

public record AuctionCancelledEvent(
        String auctionId,
        String reason,
        List<String> investmentTransactionIds,
        Instant occurredAt
) {
}
