package com.liquilabs.vankoo.investment.interfaces.events.resources;

public sealed interface AuctionLifecycleEventData permits
        AuctionCreatedIntegrationEventData,
        AuctionPublishedIntegrationEventData,
        PartitionAddedIntegrationEventData,
        AuctionFullyFundedIntegrationEventData,
        AuctionExpiredIntegrationEventData,
        AuctionCancelledIntegrationEventData,
        AuctionClosedIntegrationEventData {
}
