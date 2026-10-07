package com.liquilabs.vankoo.investment.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;

public record PartitionAddedEvent(
        String auctionId,
        String partitionId,
        BigDecimal addedAmount,
        BigDecimal newCurrentFunding,
        Instant occurredAt
) {}
