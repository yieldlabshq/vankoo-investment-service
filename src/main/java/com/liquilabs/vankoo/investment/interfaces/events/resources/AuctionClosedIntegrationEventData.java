package com.liquilabs.vankoo.investment.interfaces.events.resources;

public record AuctionClosedIntegrationEventData(
        String closingTransactionId
) implements AuctionLifecycleEventData {
}
