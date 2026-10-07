package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;

public record CloseAuctionCommand(AuctionId auctionId, String transactionId) {}
