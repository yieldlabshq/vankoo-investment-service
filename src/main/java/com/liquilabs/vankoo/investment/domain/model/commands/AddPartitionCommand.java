package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;

public record AddPartitionCommand(
        AuctionId auctionId,
        UserId investorId,
        Money amount,
        String transactionId
) {}
