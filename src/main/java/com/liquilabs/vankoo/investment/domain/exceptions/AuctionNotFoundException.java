package com.liquilabs.vankoo.investment.domain.exceptions;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(String auctionId) {
        super("Auction not found: " + auctionId);
    }
}
