package com.liquilabs.vankoo.investment.domain.services;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.math.BigDecimal;

public record FinancialCalculation(
        String pricingVersion,
        ScoreGrade riskGrade,
        Currency currency,
        int termDays,
        BigDecimal fundableAmount,
        BigDecimal investorTea,
        BigDecimal investorTermRate,
        BigDecimal fundingTarget,
        BigDecimal investorGrossProfit,
        BigDecimal platformMonthlyFeeRate,
        BigDecimal platformFeeBase,
        BigDecimal platformFeeTaxRate,
        BigDecimal platformFeeTax,
        BigDecimal platformFeeTotal,
        BigDecimal mypeAdvance,
        BigDecimal mypeTotalCost,
        BigDecimal mypeTcea
) {
}
