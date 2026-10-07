package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Map;

public record PricingParameters(
        String version,
        int dayCountBasis,
        BigDecimal platformMonthlyFeeRate,
        BigDecimal platformFeeTaxRate,
        Map<ScoreGrade, BigDecimal> investorTea
) {
    public PricingParameters {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Pricing version is required");
        }
        if (dayCountBasis <= 0) {
            throw new IllegalArgumentException("Day-count basis must be positive");
        }
        requireNonNegative(platformMonthlyFeeRate, "Platform monthly fee rate");
        requireNonNegative(platformFeeTaxRate, "Platform fee tax rate");
        investorTea = Map.copyOf(investorTea);
        for (ScoreGrade grade : new ScoreGrade[]{ScoreGrade.A, ScoreGrade.B, ScoreGrade.C}) {
            requireNonNegative(investorTea.get(grade), "Investor TEA for " + grade);
        }
    }

    public BigDecimal teaFor(ScoreGrade grade) {
        BigDecimal rate = investorTea.get(grade);
        if (rate == null) {
            throw new IllegalArgumentException("No investor TEA configured for risk grade " + grade);
        }
        return rate;
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
