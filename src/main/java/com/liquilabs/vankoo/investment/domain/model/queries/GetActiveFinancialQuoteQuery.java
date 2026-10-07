package com.liquilabs.vankoo.investment.domain.model.queries;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;

public record GetActiveFinancialQuoteQuery(AuctionId auctionId, UserId requesterId) {}
