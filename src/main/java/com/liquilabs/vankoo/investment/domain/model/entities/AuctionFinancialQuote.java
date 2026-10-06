package com.liquilabs.vankoo.investment.domain.model.entities;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.QuoteStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.domain.services.FinancialCalculation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auction_financial_quotes")
@Getter
@NoArgsConstructor
public class AuctionFinancialQuote {

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status;

    @Column(nullable = false, length = 40)
    private String pricingVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScoreGrade riskGrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false)
    private int termDays;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fundableAmount;

    @Column(nullable = false, precision = 19, scale = 12)
    private BigDecimal investorTea;

    @Column(nullable = false, precision = 19, scale = 12)
    private BigDecimal investorTermRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fundingTarget;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal investorGrossProfit;

    @Column(nullable = false, precision = 19, scale = 12)
    private BigDecimal platformMonthlyFeeRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFeeBase;

    @Column(nullable = false, precision = 19, scale = 12)
    private BigDecimal platformFeeTaxRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFeeTax;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFeeTotal;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal mypeAdvance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal mypeTotalCost;

    @Column(nullable = false, precision = 19, scale = 12)
    private BigDecimal mypeTcea;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant validUntil;

    private Instant acceptedAt;

    public AuctionFinancialQuote(FinancialCalculation calculation, Instant createdAt, Duration validity) {
        this.id = UUID.randomUUID().toString();
        this.status = QuoteStatus.ACTIVE;
        this.pricingVersion = calculation.pricingVersion();
        this.riskGrade = calculation.riskGrade();
        this.currency = calculation.currency();
        this.termDays = calculation.termDays();
        this.fundableAmount = calculation.fundableAmount();
        this.investorTea = calculation.investorTea();
        this.investorTermRate = calculation.investorTermRate();
        this.fundingTarget = calculation.fundingTarget();
        this.investorGrossProfit = calculation.investorGrossProfit();
        this.platformMonthlyFeeRate = calculation.platformMonthlyFeeRate();
        this.platformFeeBase = calculation.platformFeeBase();
        this.platformFeeTaxRate = calculation.platformFeeTaxRate();
        this.platformFeeTax = calculation.platformFeeTax();
        this.platformFeeTotal = calculation.platformFeeTotal();
        this.mypeAdvance = calculation.mypeAdvance();
        this.mypeTotalCost = calculation.mypeTotalCost();
        this.mypeTcea = calculation.mypeTcea();
        this.createdAt = createdAt;
        this.validUntil = createdAt.plus(validity);
    }

    public boolean isActiveAt(Instant instant) {
        return status == QuoteStatus.ACTIVE && instant.isBefore(validUntil);
    }

    public void accept(Instant instant) {
        if (!isActiveAt(instant)) {
            expireIfNecessary(instant);
            throw new IllegalStateException("Financial quote is not active");
        }
        status = QuoteStatus.ACCEPTED;
        acceptedAt = instant;
    }

    public void supersede() {
        if (status == QuoteStatus.ACTIVE) {
            status = QuoteStatus.SUPERSEDED;
        }
    }

    public void expireIfNecessary(Instant instant) {
        if (status == QuoteStatus.ACTIVE && !instant.isBefore(validUntil)) {
            status = QuoteStatus.EXPIRED;
        }
    }
}
