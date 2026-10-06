package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.InvoiceId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;
import com.liquilabs.vankoo.investment.domain.services.AuctionPricingCalculator;
import com.liquilabs.vankoo.investment.domain.services.AuctionPricingCalculatorTest;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.repositories.AuctionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({
        InvestmentOutboxEventListener.class,
        AuctionIntegrationEventMapper.class,
        InvestmentOutboxTransactionTest.OutboxTestConfiguration.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvestmentOutboxTransactionTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void commitsAuctionAndOutboxTogether() {
        Auction auction = auction("invoice-outbox-commit");
        auction.registerAuctionCreatedEvent(NOW);
        auctionRepository.save(auction);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(auctionRepository.findById(auction.getId())).isPresent();
        assertThat(outboxRepository.findByAggregateIdOrderBySequence(auction.getId().uuid()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                    assertThat(event.getPayload()).contains("\"eventType\":\"AuctionCreated\"");
                });
    }

    @Test
    void rollsBackAuctionAndOutboxTogether() {
        Auction auction = auction("invoice-outbox-rollback");
        auction.registerAuctionCreatedEvent(NOW);
        auctionRepository.save(auction);

        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(auctionRepository.findById(auction.getId())).isEmpty();
        assertThat(outboxRepository.findByAggregateIdOrderBySequence(auction.getId().uuid())).isEmpty();
    }

    @Test
    void acceptingAQuotePersistsTheSelfContainedPublishedEvent() {
        Auction auction = auction("invoice-outbox-published");
        auction.evaluate("assessment-outbox", ScoreGrade.B, true, NOW);
        var calculator = new AuctionPricingCalculator();
        var calculation = calculator.calculate(
                AuctionPricingCalculatorTest.pricingParameters(),
                auction.getFundableAmount(),
                ScoreGrade.B,
                LocalDate.of(2026, 9, 14),
                auction.getDueDate()
        );
        var quote = auction.createQuote(calculation, NOW, Duration.ofHours(24));
        auction.acceptQuote(
                quote.getId(), NOW, Duration.ofDays(7), Duration.ofDays(1), ZoneId.of("America/Lima")
        );
        auctionRepository.save(auction);

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(outboxRepository.findByAggregateIdOrderBySequence(auction.getId().uuid()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo("AuctionPublished");
                    assertThat(event.getPayload()).contains("\"targetAmount\"");
                    assertThat(event.getPayload()).contains("\"expiresAt\"");
                });
    }

    private static Auction auction(String invoiceId) {
        return new Auction(
                new InvoiceId(invoiceId),
                new UserId("mype-outbox"),
                new Money(new BigDecimal("10000.00"), Currency.PEN),
                true,
                "20123456789",
                "Pagador Outbox S.A.",
                LocalDate.of(2026, 11, 14)
        );
    }

    @TestConfiguration
    static class OutboxTestConfiguration {

        @Bean
        Clock testClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper testObjectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
