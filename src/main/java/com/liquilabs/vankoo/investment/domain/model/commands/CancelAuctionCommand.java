package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;

public record CancelAuctionCommand(AuctionId auctionId, String reason, boolean internal) {
}
