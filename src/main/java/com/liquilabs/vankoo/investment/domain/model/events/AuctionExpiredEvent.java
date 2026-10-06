package com.liquilabs.vankoo.investment.domain.model.events;

import java.time.Instant;
import java.util.List;

public record AuctionExpiredEvent(
        String auctionId,
        List<String> investmentTransactionIds,
        Instant occurredAt
) {
}
