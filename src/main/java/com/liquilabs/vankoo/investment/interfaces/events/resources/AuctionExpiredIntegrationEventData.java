package com.liquilabs.vankoo.investment.interfaces.events.resources;

import java.util.List;

public record AuctionExpiredIntegrationEventData(
        List<String> releasedInvestmentTransactionIds
) implements AuctionLifecycleEventData {
}
