package com.liquilabs.vankoo.investment.interfaces.events.resources;

public final class AuctionLifecycleEventContract {

    public static final String TOPIC = "investment.auction-lifecycle.v1";
    public static final String DEAD_LETTER_TOPIC = TOPIC + ".dlq";
    public static final int SCHEMA_VERSION = 1;

    public static final String AUCTION_CREATED = "AuctionCreated";
    public static final String AUCTION_PUBLISHED = "AuctionPublished";
    public static final String PARTITION_ADDED = "PartitionAdded";
    public static final String AUCTION_FULLY_FUNDED = "AuctionFullyFunded";
    public static final String AUCTION_EXPIRED = "AuctionExpired";
    public static final String AUCTION_CANCELLED = "AuctionCancelled";
    public static final String AUCTION_CLOSED = "AuctionClosed";

    private AuctionLifecycleEventContract() {
    }
}
