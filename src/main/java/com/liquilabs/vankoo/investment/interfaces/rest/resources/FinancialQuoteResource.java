package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.QuoteStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record FinancialQuoteResource(
        String quoteId,
        QuoteStatus status,
        String pricingVersion,
        ScoreGrade riskGrade,
        String currency,
        int termDays,
        BigDecimal fundableAmount,
        BigDecimal investorTeaPct,
        BigDecimal investorTermRatePct,
        BigDecimal fundingTarget,
        BigDecimal investorGrossProfit,
        BigDecimal platformMonthlyFeeRatePct,
        BigDecimal platformFeeBase,
        BigDecimal platformFeeTaxRatePct,
        BigDecimal platformFeeTax,
        BigDecimal platformFeeTotal,
        BigDecimal mypeAdvance,
        BigDecimal mypeTotalCost,
        BigDecimal mypeTceaPct,
        Instant createdAt,
        Instant validUntil,
        Instant acceptedAt
) {
    public static FinancialQuoteResource from(AuctionFinancialQuote quote) {
        return new FinancialQuoteResource(
                quote.getId(),
                quote.getStatus(),
                quote.getPricingVersion(),
                quote.getRiskGrade(),
                quote.getCurrency().name(),
                quote.getTermDays(),
                quote.getFundableAmount(),
                percentagePoints(quote.getInvestorTea()),
                percentagePoints(quote.getInvestorTermRate()),
                quote.getFundingTarget(),
                quote.getInvestorGrossProfit(),
                percentagePoints(quote.getPlatformMonthlyFeeRate()),
                quote.getPlatformFeeBase(),
                percentagePoints(quote.getPlatformFeeTaxRate()),
                quote.getPlatformFeeTax(),
                quote.getPlatformFeeTotal(),
                quote.getMypeAdvance(),
                quote.getMypeTotalCost(),
                percentagePoints(quote.getMypeTcea()),
                quote.getCreatedAt(),
                quote.getValidUntil(),
                quote.getAcceptedAt()
        );
    }

    private static BigDecimal percentagePoints(BigDecimal fraction) {
        return fraction.multiply(new BigDecimal("100")).setScale(6, RoundingMode.HALF_UP);
    }
}
