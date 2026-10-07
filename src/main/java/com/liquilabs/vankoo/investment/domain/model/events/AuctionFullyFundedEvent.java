package com.liquilabs.vankoo.investment.domain.model.events;

import java.time.Instant;

public record AuctionFullyFundedEvent(
        String auctionId,
        Instant occurredAt
) {}
