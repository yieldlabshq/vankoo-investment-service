package com.liquilabs.vankoo.investment.domain.services;

import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.queries.*;

import java.util.List;
import java.util.Optional;

public interface AuctionQueryService {
    Optional<Auction> handle(GetAuctionByIdQuery query);
    Optional<AuctionFinancialQuote> handle(GetActiveFinancialQuoteQuery query);
    List<Auction> handle(GetAllActiveAuctionsQuery query);
    AuctionMarketplacePage handle(GetMarketplaceAuctionsQuery query);
    List<Auction> handle(GetAuctionsByInvestorQuery query);
    List<Auction> handle(GetAuctionsByMypeQuery query);
}
