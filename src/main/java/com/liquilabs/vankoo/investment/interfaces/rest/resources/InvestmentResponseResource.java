package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import com.liquilabs.vankoo.investment.domain.model.entities.Partition;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.PartitionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InvestmentResponseResource(
        String partitionId,
        String auctionId,
        String investorId,
        BigDecimal amount,
        String currency,
        BigDecimal participationPct,
        BigDecimal expectedMaturityAmount,
        BigDecimal expectedGrossProfit,
        BigDecimal returnRatePct,
        PartitionStatus status,
        Instant purchasedAt,
        String transactionId
) {
    public static InvestmentResponseResource from(Partition partition) {
        return new InvestmentResponseResource(
                partition.getId().uuid(),
                partition.getAuctionId().uuid(),
                partition.getInvestorId().uuid(),
                partition.getAmount().amount(),
                partition.getAmount().currency().name(),
                partition.getPercentage().value(),
                partition.getExpectedReturn().amount(),
                partition.getExpectedReturn().amount().subtract(partition.getAmount().amount()),
                partition.getReturnRate().value(),
                partition.getStatus(),
                partition.getPurchasedAt(),
                partition.getInvestmentTransactionId()
        );
    }
}
