package com.liquilabs.vankoo.investment.domain.exceptions;

public class ActiveQuoteNotFoundException extends RuntimeException {
    public ActiveQuoteNotFoundException(String auctionId) {
        super("Auction has no active financial quote: " + auctionId);
    }
}
