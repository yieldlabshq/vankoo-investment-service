package com.liquilabs.vankoo.investment.interfaces.rest.transform;

import com.liquilabs.vankoo.investment.domain.model.commands.AddPartitionCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.CreateInvestmentResource;

public class CreatePartitionCommandFromResourceAssembler {

    public static AddPartitionCommand toCommandFromResource(AuctionId auctionId, CreateInvestmentResource resource) {
        return new AddPartitionCommand(
                auctionId,
                new UserId(resource.investorId()),
                new Money(resource.amount(), Currency.valueOf(resource.currency().toUpperCase())),
                resource.transactionId()
        );
    }
}
