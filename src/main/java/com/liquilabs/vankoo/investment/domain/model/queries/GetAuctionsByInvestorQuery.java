package com.liquilabs.vankoo.investment.domain.model.queries;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;

public record GetAuctionsByInvestorQuery(UserId investorId) {
}
