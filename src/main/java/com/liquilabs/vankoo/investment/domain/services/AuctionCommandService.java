package com.liquilabs.vankoo.investment.domain.services;

import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.entities.Partition;
import com.liquilabs.vankoo.investment.domain.model.commands.AcceptFinancialQuoteCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.AddPartitionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.CancelAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.CloseAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.CreateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.CreateFinancialQuoteCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.EvaluateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;

public interface AuctionCommandService {
    AuctionId handle(CreateAuctionCommand command);
    Auction handle(EvaluateAuctionCommand command);
    AuctionFinancialQuote handle(CreateFinancialQuoteCommand command);
    Auction handle(AcceptFinancialQuoteCommand command);
    Partition handle(AddPartitionCommand command);
    Auction handle(CloseAuctionCommand command);
    Auction handle(CancelAuctionCommand command);
    int expireDueQuotes();
    int expireDueAuctions();
}
