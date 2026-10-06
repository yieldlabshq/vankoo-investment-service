package com.liquilabs.vankoo.investment.interfaces.events.resources;

import java.util.List;

public record AuctionCancelledIntegrationEventData(
        String reason,
        List<String> releasedInvestmentTransactionIds
) implements AuctionLifecycleEventData {
}
