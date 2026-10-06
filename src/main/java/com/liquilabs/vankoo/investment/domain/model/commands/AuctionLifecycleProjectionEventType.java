package com.liquilabs.vankoo.investment.domain.model.commands;

public enum AuctionLifecycleProjectionEventType {
    AuctionCreated,
    AuctionPublished,
    PartitionAdded,
    AuctionFullyFunded,
    AuctionExpired,
    AuctionCancelled,
    AuctionClosed
}
