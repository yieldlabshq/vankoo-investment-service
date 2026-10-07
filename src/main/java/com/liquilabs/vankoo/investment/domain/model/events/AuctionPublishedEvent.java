package com.liquilabs.vankoo.investment.domain.model.events;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AuctionPublishedEvent(
        String auctionId,
        String invoiceId,
        String mypeId,
        String payerRuc,
        String payerName,
        LocalDate dueDate,
        BigDecimal invoiceAmount,
        BigDecimal fundableAmount,
        BigDecimal targetAmount,
        BigDecimal currentFunding,
        String currency,
        BigDecimal investorTea,
        BigDecimal investorTermRate,
        int quotedTermDays,
        ScoreGrade riskGrade,
        AuctionStatus status,
        boolean greenCertified,
        Instant publishedAt,
        Instant expiresAt
) {
}
