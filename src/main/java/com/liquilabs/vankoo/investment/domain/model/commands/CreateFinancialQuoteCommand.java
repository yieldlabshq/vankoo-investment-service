package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;

public record CreateFinancialQuoteCommand(AuctionId auctionId, UserId requesterId) {
}
