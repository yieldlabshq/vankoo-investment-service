package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.liquilabs.vankoo.investment.domain.model.events.AuctionCancelledEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionClosedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionExpiredEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionFullyFundedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionPublishedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.PartitionAddedEvent;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionPublishedIntegrationEventData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionIntegrationEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private final AuctionIntegrationEventMapper mapper = new AuctionIntegrationEventMapper();

    @Test
    void mapsEverySupportedDomainEventToTheFrozenWireType() {
        List<Object> domainEvents = List.of(
                new AuctionCreatedEvent(
                        "auction-1", "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                        LocalDate.of(2026, 11, 14), new BigDecimal("10000.00"), "PEN",
                        AuctionStatus.PENDING_VERIFICATION_RISK, true, NOW
                ),
                publishedEvent(),
                new PartitionAddedEvent(
                        "auction-1", "partition-1", new BigDecimal("500.00"),
                        new BigDecimal("500.00"), NOW
                ),
                new AuctionFullyFundedEvent("auction-1", NOW),
                new AuctionExpiredEvent("auction-1", List.of("tx-1"), NOW),
                new AuctionCancelledEvent("auction-1", "cancelled", List.of("tx-2"), NOW),
                new AuctionClosedEvent("auction-1", "close-tx", NOW)
        );

        assertThat(domainEvents.stream().map(mapper::map).map(MappedAuctionIntegrationEvent::eventType))
                .containsExactly(
                        AuctionLifecycleEventContract.AUCTION_CREATED,
                        AuctionLifecycleEventContract.AUCTION_PUBLISHED,
                        AuctionLifecycleEventContract.PARTITION_ADDED,
                        AuctionLifecycleEventContract.AUCTION_FULLY_FUNDED,
                        AuctionLifecycleEventContract.AUCTION_EXPIRED,
                        AuctionLifecycleEventContract.AUCTION_CANCELLED,
                        AuctionLifecycleEventContract.AUCTION_CLOSED
                );
    }

    @Test
    void mapsPublishedAsASelfContainedMarketplaceSnapshot() {
        MappedAuctionIntegrationEvent mapped = mapper.map(publishedEvent());

        assertThat(mapped.data()).isInstanceOfSatisfying(
                AuctionPublishedIntegrationEventData.class,
                data -> {
                    assertThat(data.targetAmount()).isEqualByComparingTo("9884.00");
                    assertThat(data.currentFunding()).isEqualByComparingTo("0.00");
                    assertThat(data.investorTea()).isEqualByComparingTo("0.15");
                    assertThat(data.status()).isEqualTo("PUBLISHED");
                }
        );
    }

    private static AuctionPublishedEvent publishedEvent() {
        return new AuctionPublishedEvent(
                "auction-1", "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                LocalDate.of(2026, 11, 14), new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                new BigDecimal("9884.00"), new BigDecimal("0.00"), "PEN", new BigDecimal("0.15"),
                new BigDecimal("0.011735"), 30, ScoreGrade.B, AuctionStatus.PUBLISHED, true,
                NOW, NOW.plusSeconds(604800)
        );
    }
}
