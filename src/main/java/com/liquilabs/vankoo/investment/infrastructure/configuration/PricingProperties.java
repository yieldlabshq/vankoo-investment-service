package com.liquilabs.vankoo.investment.infrastructure.configuration;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

@ConfigurationProperties(prefix = "vankoo.investment.pricing")
public record PricingProperties(
        String version,
        String zoneId,
        int dayCountBasis,
        Duration quoteValidity,
        Duration fundingWindow,
        Duration settlementBuffer,
        BigDecimal platformMonthlyFeeRate,
        BigDecimal platformFeeTaxRate,
        Map<ScoreGrade, BigDecimal> investorTea,
        Map<Currency, BigDecimal> minimumInvestment
) {
    public PricingProperties {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Pricing version is required");
        }
        ZoneId.of(zoneId);
        if (dayCountBasis <= 0) {
            throw new IllegalArgumentException("Day-count basis must be positive");
        }
        requirePositive(quoteValidity, "Quote validity");
        requirePositive(fundingWindow, "Funding window");
        requirePositive(settlementBuffer, "Settlement buffer");
        requireNonNegative(platformMonthlyFeeRate, "Platform monthly fee rate");
        requireNonNegative(platformFeeTaxRate, "Platform fee tax rate");
        investorTea = Map.copyOf(investorTea);
        minimumInvestment = Map.copyOf(minimumInvestment);
        for (ScoreGrade grade : new ScoreGrade[]{ScoreGrade.A, ScoreGrade.B, ScoreGrade.C}) {
            requireNonNegative(investorTea.get(grade), "Investor TEA for " + grade);
        }
        for (Currency currency : Currency.values()) {
            BigDecimal minimum = minimumInvestment.get(currency);
            if (minimum == null || minimum.signum() <= 0) {
                throw new IllegalArgumentException("Minimum investment for " + currency + " must be positive");
            }
        }
    }

    public ZoneId pricingZone() {
        return ZoneId.of(zoneId);
    }

    public BigDecimal teaFor(ScoreGrade grade) {
        BigDecimal rate = investorTea.get(grade);
        if (rate == null) {
            throw new IllegalArgumentException("No investor TEA configured for risk grade " + grade);
        }
        return rate;
    }

    public BigDecimal minimumFor(Currency currency) {
        return minimumInvestment.get(currency);
    }

    private static void requirePositive(Duration duration, String field) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
