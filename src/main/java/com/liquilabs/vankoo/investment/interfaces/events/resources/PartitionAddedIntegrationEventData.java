package com.liquilabs.vankoo.investment.interfaces.events.resources;

import java.math.BigDecimal;

public record PartitionAddedIntegrationEventData(
        String partitionId,
        BigDecimal addedAmount,
        BigDecimal currentFunding
) implements AuctionLifecycleEventData {
}
