package com.liquilabs.vankoo.investment.domain.model.aggregates;

import com.liquilabs.vankoo.investment.domain.exceptions.UnauthorizedAccessException;
import com.liquilabs.vankoo.investment.domain.model.entities.Partition;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.domain.services.AuctionPricingCalculator;
import com.liquilabs.vankoo.investment.domain.services.AuctionPricingCalculatorTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-09-07T17:00:00Z");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 6);
    private final AuctionPricingCalculator calculator = new AuctionPricingCalculator();

    @Test
    void startsInPendingVerificationRiskWithNoFundingAndNoRiskGrade() {
        Auction auction = newAuction();

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PENDING_VERIFICATION_RISK);
        assertThat(auction.getCurrentFunding().amount()).isEqualByComparingTo("0.00");
        assertThat(auction.getCurrentFunding().currency()).isEqualTo(Currency.PEN);
        assertThat(auction.getRiskScore().grade()).isEqualTo(ScoreGrade.UNDER_EVALUATION);
        assertThat(auction.getFundableAmount()).isNull();
        assertThat(auction.getPartitions()).isEmpty();
    }

    @Test
    void rejectsAnInvestmentBelowTheMinimumTicketWhenThereIsAmpleFundingRoomLeft() {
        Auction auction = evaluatedAndPublishedAuction();

        assertThatThrownBy(() -> auction.addInvestment(
                new UserId("investor-1"), new Money(new BigDecimal("200.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-below-minimum", NOW.plusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minimum");

        assertThat(auction.getCurrentFunding().amount()).isEqualByComparingTo("0.00");
        assertThat(auction.getPartitions()).isEmpty();
    }

    @Test
    void acceptsAQuoteAndAllocatesTheReceivableExactlyAcrossPartitions() {
        Auction auction = evaluatedAuction();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(), auction.getFundableAmount(), ScoreGrade.B, LocalDate.of(2026, 9, 7), DUE_DATE);
        var quote = auction.createQuote(calculation, NOW, Duration.ofHours(24));

        auction.acceptQuote(quote.getId(), NOW, Duration.ofDays(7), Duration.ofDays(1), java.time.ZoneId.of("America/Lima"));
        Partition first = auction.addInvestment(
                new UserId("investor-1"), new Money(new BigDecimal("500.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-1", NOW.plusSeconds(1)
        );
        BigDecimal remaining = auction.getTargetAmount().amount().subtract(auction.getCurrentFunding().amount());
        Partition last = auction.addInvestment(
                new UserId("investor-2"), new Money(remaining, Currency.PEN),
                new BigDecimal("500.00"), "tx-2", NOW.plusSeconds(2)
        );

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FULLY_FUNDED);
        assertThat(auction.getCurrentFunding().amount()).isEqualByComparingTo("9769.76");
        assertThat(first.getExpectedReturn().amount().add(last.getExpectedReturn().amount()))
                .isEqualByComparingTo("10000.00");
        assertThat(auction.addInvestment(
                new UserId("investor-2"), new Money(remaining, Currency.PEN),
                new BigDecimal("500.00"), "tx-2", NOW.plusSeconds(3)
        ).getId()).isEqualTo(last.getId());
        assertThat(auction.getPartitions()).hasSize(2);
    }

    @Test
    void permitsABelowMinimumTicketOnlyWhenItIsTheExactRemainder() {
        Auction auction = evaluatedAndPublishedAuction();
        BigDecimal amountLeavingSmallRemainder = auction.getTargetAmount().amount().subtract(new BigDecimal("100.00"));
        auction.addInvestment(
                new UserId("investor-1"), new Money(amountLeavingSmallRemainder, Currency.PEN),
                new BigDecimal("500.00"), "tx-large", NOW.plusSeconds(1)
        );

        assertThatThrownBy(() -> auction.addInvestment(
                new UserId("investor-2"), new Money(new BigDecimal("99.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-too-small", NOW.plusSeconds(2)
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minimum");

        auction.addInvestment(
                new UserId("investor-2"), new Money(new BigDecimal("100.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-remainder", NOW.plusSeconds(3)
        );
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FULLY_FUNDED);
    }

    @Test
    void expiresPartialFundingAsAllOrNothing() {
        Auction auction = evaluatedAndPublishedAuction();
        Partition partition = auction.addInvestment(
                new UserId("investor-1"), new Money(new BigDecimal("500.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-expiring", NOW.plusSeconds(1)
        );

        boolean changed = auction.expireIfDue(NOW.plus(Duration.ofDays(8)));

        assertThat(changed).isTrue();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.EXPIRED);
        assertThat(partition.getStatus()).isEqualTo(PartitionStatus.CANCELLED);
        assertThat(auction.getCurrentFunding().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void doesNotExpireAuctionsThatWereNeverPublished() {
        Auction auction = evaluatedAuction();

        boolean changed = auction.expireIfDue(NOW.plus(Duration.ofDays(365)));

        assertThat(changed).isFalse();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.DRAFT);
        assertThat(auction.getExpiresAt()).isNull();
    }

    @Test
    void doesNotExpireOrReEmitOnceAnAuctionAlreadyExpired() {
        Auction auction = evaluatedAndPublishedAuction();
        auction.addInvestment(
                new UserId("investor-1"), new Money(new BigDecimal("500.00"), Currency.PEN),
                new BigDecimal("500.00"), "tx-expiring", NOW.plusSeconds(1)
        );
        Instant expirationCheck = NOW.plus(Duration.ofDays(8));
        boolean firstAttempt = auction.expireIfDue(expirationCheck);
        Instant cancelledAtAfterFirstExpiration = auction.getCancelledAt();

        boolean secondAttempt = auction.expireIfDue(expirationCheck.plus(Duration.ofDays(30)));

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.EXPIRED);
        assertThat(auction.getCancelledAt()).isEqualTo(cancelledAtAfterFirstExpiration);
    }

    @Test
    void rejectsEvaluationWhenTheFullBalanceIsNotOutstanding() {
        Auction auction = newAuction();
        assertThatThrownBy(() -> auction.evaluate("risk-1", ScoreGrade.A, false, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pilot only accepts");
    }

    @Test
    void supersedesThePreviousUnacceptedQuote() {
        Auction auction = evaluatedAuction();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(), auction.getFundableAmount(), ScoreGrade.B, LocalDate.of(2026, 9, 7), DUE_DATE);
        var first = auction.createQuote(calculation, NOW, Duration.ofHours(24));
        var second = auction.createQuote(calculation, NOW.plusSeconds(60), Duration.ofHours(24));

        assertThat(first.getStatus()).isEqualTo(QuoteStatus.SUPERSEDED);
        assertThat(second.getStatus()).isEqualTo(QuoteStatus.ACTIVE);
        assertThatThrownBy(() -> auction.acceptQuote(
                first.getId(), NOW.plusSeconds(120), Duration.ofDays(7), Duration.ofDays(1), java.time.ZoneId.of("America/Lima")
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("not active");
    }

    @Test
    void rejectsAnExpiredQuote() {
        Auction auction = evaluatedAuction();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(), auction.getFundableAmount(), ScoreGrade.B, LocalDate.of(2026, 9, 7), DUE_DATE);
        var quote = auction.createQuote(calculation, NOW, Duration.ofHours(24));

        assertThatThrownBy(() -> auction.acceptQuote(
                quote.getId(), NOW.plus(Duration.ofHours(25)), Duration.ofDays(7), Duration.ofDays(1), java.time.ZoneId.of("America/Lima")
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("not active");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void exposesOnlyTheQuoteThatCanStillBeAccepted() {
        Auction auction = evaluatedAuction();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(), auction.getFundableAmount(), ScoreGrade.B, LocalDate.of(2026, 9, 7), DUE_DATE);
        assertThat(auction.activeQuote(NOW)).isEmpty();

        auction.createQuote(calculation, NOW, Duration.ofHours(24));
        var second = auction.createQuote(calculation, NOW.plusSeconds(60), Duration.ofHours(24));

        assertThat(auction.activeQuote(NOW.plusSeconds(120))).contains(second);
        assertThat(auction.activeQuote(NOW.plus(Duration.ofHours(25)))).isEmpty();

        auction.acceptQuote(second.getId(), NOW.plusSeconds(120), Duration.ofDays(7), Duration.ofDays(1), java.time.ZoneId.of("America/Lima"));
        assertThat(auction.activeQuote(NOW.plusSeconds(180))).isEmpty();
    }

    @Test
    void rejectsARequesterThatDoesNotOwnTheAuction() {
        Auction auction = newAuction();

        auction.ensureOwnedBy(new UserId("mype-1"));
        assertThatThrownBy(() -> auction.ensureOwnedBy(new UserId("mype-2")))
                .isInstanceOf(UnauthorizedAccessException.class);
        assertThatThrownBy(() -> auction.ensureOwnedBy(null))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    private Auction evaluatedAndPublishedAuction() {
        Auction auction = evaluatedAuction();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(), auction.getFundableAmount(), ScoreGrade.B, LocalDate.of(2026, 9, 7), DUE_DATE);
        var quote = auction.createQuote(calculation, NOW, Duration.ofHours(24));
        auction.acceptQuote(quote.getId(), NOW, Duration.ofDays(7), Duration.ofDays(1), java.time.ZoneId.of("America/Lima"));
        return auction;
    }

    private Auction evaluatedAuction() {
        Auction auction = newAuction();
        auction.evaluate("risk-1", ScoreGrade.B, true, NOW);
        return auction;
    }

    private Auction newAuction() {
        return new Auction(
                new InvoiceId("invoice-1"),
                new UserId("mype-1"),
                new Money(new BigDecimal("10000.00"), Currency.PEN),
                false,
                "20123456789",
                "Pagador S.A.",
                DUE_DATE
        );
    }
}
