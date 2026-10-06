package com.liquilabs.vankoo.investment.domain.services;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.PricingParameters;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuctionPricingCalculatorTest {

    private final AuctionPricingCalculator calculator = new AuctionPricingCalculator();

    @Test
    void calculatesTheApprovedSixtyDayGradeBExample() {
        FinancialCalculation result = calculator.calculate(
                pricingParameters(),
                new Money(new BigDecimal("10000.00"), Currency.PEN),
                ScoreGrade.B,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 11, 6)
        );

        assertThat(result.termDays()).isEqualTo(60);
        assertThat(result.investorTermRate()).isEqualByComparingTo("0.023567073118");
        assertThat(result.fundingTarget()).isEqualByComparingTo("9769.76");
        assertThat(result.investorGrossProfit()).isEqualByComparingTo("230.24");
        assertThat(result.platformFeeBase()).isEqualByComparingTo("60.00");
        assertThat(result.platformFeeTax()).isEqualByComparingTo("10.80");
        assertThat(result.platformFeeTotal()).isEqualByComparingTo("70.80");
        assertThat(result.mypeAdvance()).isEqualByComparingTo("9698.96");
        assertThat(result.mypeTotalCost()).isEqualByComparingTo("301.04");
        assertThat(result.mypeTcea()).isEqualByComparingTo("0.201293122122");
    }

    @Test
    void rejectsAnInvoiceThatHasAlreadyMatured() {
        assertThatThrownBy(() -> calculator.calculate(
                pricingParameters(),
                new Money(new BigDecimal("1000.00"), Currency.PEN),
                ScoreGrade.A,
                LocalDate.of(2026, 9, 7),
                LocalDate.of(2026, 9, 7)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due date");
    }

    @ParameterizedTest
    @CsvSource({
            "A,30,0.120000000000",
            "B,60,0.150000000000",
            "C,90,0.180000000000"
    })
    void appliesTheConfiguredTeaAcrossSupportedTerms(ScoreGrade grade, int days, String expectedTea) {
        LocalDate valuationDate = LocalDate.of(2026, 9, 7);
        FinancialCalculation result = calculator.calculate(
                pricingParameters(),
                new Money(new BigDecimal("10000.00"), Currency.USD),
                grade,
                valuationDate,
                valuationDate.plusDays(days)
        );

        assertThat(result.investorTea()).isEqualByComparingTo(expectedTea);
        assertThat(result.termDays()).isEqualTo(days);
        assertThat(result.fundingTarget().add(result.investorGrossProfit())).isEqualByComparingTo("10000.00");
        assertThat(result.mypeAdvance().add(result.mypeTotalCost())).isEqualByComparingTo("10000.00");
        assertThat(result.platformFeeTax()).isEqualByComparingTo(
                result.platformFeeBase().multiply(new BigDecimal("0.18")).setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }

    @Test
    void rejectsMoneyWithSubCentPrecision() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.001"), Currency.PEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
    }

    public static PricingParameters pricingParameters() {
        return new PricingParameters(
                "test-2026-09",
                360,
                new BigDecimal("0.003"),
                new BigDecimal("0.18"),
                Map.of(ScoreGrade.A, new BigDecimal("0.12"), ScoreGrade.B, new BigDecimal("0.15"), ScoreGrade.C, new BigDecimal("0.18"))
        );
    }
}
