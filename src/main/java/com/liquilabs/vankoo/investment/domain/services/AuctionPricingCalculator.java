package com.liquilabs.vankoo.investment.domain.services;

import ch.obermuhlner.math.big.BigDecimalMath;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.PricingParameters;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class AuctionPricingCalculator {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

    public FinancialCalculation calculate(
            PricingParameters pricing,
            Money fundableAmount,
            ScoreGrade riskGrade,
            LocalDate valuationDate,
            LocalDate dueDate
    ) {
        if (fundableAmount == null || fundableAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Fundable amount must be positive");
        }
        if (riskGrade == null || riskGrade == ScoreGrade.UNDER_EVALUATION) {
            throw new IllegalArgumentException("A final risk grade is required");
        }

        long days = ChronoUnit.DAYS.between(valuationDate, dueDate);
        if (days <= 0 || days > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invoice due date must be after the valuation date");
        }

        BigDecimal face = money(fundableAmount.amount());
        BigDecimal tea = pricing.teaFor(riskGrade);
        BigDecimal termExponent = BigDecimal.valueOf(days)
                .divide(BigDecimal.valueOf(pricing.dayCountBasis()), MATH_CONTEXT);
        BigDecimal termRate = BigDecimalMath.pow(ONE.add(tea), termExponent, MATH_CONTEXT)
                .subtract(ONE);

        BigDecimal fundingTarget = money(face.divide(ONE.add(termRate), MATH_CONTEXT));
        BigDecimal investorProfit = money(face.subtract(fundingTarget));
        BigDecimal feeBase = money(face
                .multiply(pricing.platformMonthlyFeeRate(), MATH_CONTEXT)
                .multiply(BigDecimal.valueOf(days), MATH_CONTEXT)
                .divide(DAYS_PER_MONTH, MATH_CONTEXT));
        BigDecimal feeTax = money(feeBase.multiply(pricing.platformFeeTaxRate(), MATH_CONTEXT));
        BigDecimal feeTotal = money(feeBase.add(feeTax));
        BigDecimal mypeAdvance = money(fundingTarget.subtract(feeTotal));

        if (mypeAdvance.signum() <= 0) {
            throw new IllegalArgumentException("Platform charges leave no positive advance for the MYPE");
        }

        BigDecimal mypeCost = money(face.subtract(mypeAdvance));
        BigDecimal annualizationExponent = BigDecimal.valueOf(pricing.dayCountBasis())
                .divide(BigDecimal.valueOf(days), MATH_CONTEXT);
        BigDecimal mypeTcea = BigDecimalMath.pow(
                        face.divide(mypeAdvance, MATH_CONTEXT),
                        annualizationExponent,
                        MATH_CONTEXT)
                .subtract(ONE);

        return new FinancialCalculation(
                pricing.version(),
                riskGrade,
                fundableAmount.currency(),
                Math.toIntExact(days),
                face,
                rate(tea),
                rate(termRate),
                fundingTarget,
                investorProfit,
                rate(pricing.platformMonthlyFeeRate()),
                feeBase,
                rate(pricing.platformFeeTaxRate()),
                feeTax,
                feeTotal,
                mypeAdvance,
                mypeCost,
                rate(mypeTcea)
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        return value.setScale(12, RoundingMode.HALF_UP);
    }
}
