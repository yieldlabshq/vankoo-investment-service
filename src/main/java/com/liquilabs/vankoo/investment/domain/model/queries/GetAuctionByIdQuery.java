package com.liquilabs.vankoo.investment.domain.model.queries;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;

public record GetAuctionByIdQuery(AuctionId auctionId) {}