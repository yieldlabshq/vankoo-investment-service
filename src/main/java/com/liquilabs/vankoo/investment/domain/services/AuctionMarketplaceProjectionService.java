package com.liquilabs.vankoo.investment.domain.services;

import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;

public interface AuctionMarketplaceProjectionService {

    void handle(ProjectAuctionLifecycleEventCommand command);
}
