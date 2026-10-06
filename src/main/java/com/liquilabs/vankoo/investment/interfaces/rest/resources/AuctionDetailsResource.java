package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AuctionDetailsResource(
        String auctionId,
        String invoiceId,
        String mypeId,
        AuctionStatus status,
        ScoreGrade riskGrade,
        BigDecimal invoiceAmount,
        BigDecimal fundableAmount,
        BigDecimal targetAmount,
        BigDecimal currentFunding,
        String currency,
        String payerRuc,
        String payerName,
        LocalDate dueDate,
        boolean greenCertified,
        boolean fullBalanceOutstandingConfirmed,
        String assessmentId,
        Instant assessedAt,
        Instant publishedAt,
        Instant expiresAt,
        Instant closedAt,
        Instant cancelledAt,
        String cancellationReason,
        FinancialQuoteResource acceptedQuote
) {
    public static AuctionDetailsResource from(Auction auction) {
        FinancialQuoteResource quote = auction.getAcceptedQuoteId() == null
                ? null
                : FinancialQuoteResource.from(auction.acceptedQuote());
        return new AuctionDetailsResource(
                auction.getId().uuid(),
                auction.getInvoiceId().uuid(),
                auction.getMypeId().uuid(),
                auction.getStatus(),
                auction.getRiskScore().grade(),
                auction.getInvoiceAmount().amount(),
                amountOf(auction.getFundableAmount()),
                amountOf(auction.getTargetAmount()),
                auction.getCurrentFunding().amount(),
                auction.getInvoiceAmount().currency().name(),
                auction.getPayerRuc(),
                auction.getPayerName(),
                auction.getDueDate(),
                auction.isGreenCertified(),
                auction.isFullBalanceOutstandingConfirmed(),
                auction.getAssessmentId(),
                auction.getAssessedAt(),
                auction.getPublishedAt(),
                auction.getExpiresAt(),
                auction.getClosedAt(),
                auction.getCancelledAt(),
                auction.getCancellationReason(),
                quote
        );
    }

    private static BigDecimal amountOf(com.liquilabs.vankoo.investment.domain.model.valueobjects.Money money) {
        return money == null ? null : money.amount();
    }
}
