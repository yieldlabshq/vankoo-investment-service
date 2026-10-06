package com.liquilabs.vankoo.investment.domain.model.queries;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AuctionMarketplaceView(
        String auctionId,
        String invoiceId,
        String mypeId,
        String payerRuc,
        String payerName,
        BigDecimal targetAmount,
        BigDecimal currentFunding,
        BigDecimal availableAmount,
        BigDecimal progressPct,
        String currency,
        BigDecimal investorTeaPct,
        BigDecimal investorTermRatePct,
        int quotedTermDays,
        long daysToMaturity,
        ScoreGrade riskGrade,
        AuctionStatus status,
        LocalDate dueDate,
        Instant publishedAt,
        Instant expiresAt,
        boolean greenCertified
) {
}
