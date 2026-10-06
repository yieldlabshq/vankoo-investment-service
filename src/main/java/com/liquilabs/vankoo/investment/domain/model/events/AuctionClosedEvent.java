package com.liquilabs.vankoo.investment.domain.model.events;

import java.time.Instant;

public record AuctionClosedEvent(String auctionId, String closingTransactionId, Instant occurredAt) {
}
