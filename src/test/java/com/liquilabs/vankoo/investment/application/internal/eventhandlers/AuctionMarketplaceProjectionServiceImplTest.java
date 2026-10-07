package com.liquilabs.vankoo.investment.application.internal.eventhandlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.AuctionLifecycleEventParser;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewEntity;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEventRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEventStatus;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionCancelledIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionClosedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionCreatedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionExpiredIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionFullyFundedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleIntegrationEvent;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionPublishedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.PartitionAddedIntegrationEventData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@ActiveProfiles("test")
@Import({
        AuctionMarketplaceProjectionServiceImpl.class,
        AuctionLifecycleEventParser.class,
        AuctionMarketplaceProjectionServiceImplTest.ProjectionTestConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionMarketplaceProjectionServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private static final String AUCTION_ID = "85ac769a-65f5-4ab5-a266-24adf53f31ca";

    @Autowired
    private AuctionMarketplaceProjectionServiceImpl projectionService;

    @Autowired
    private AuctionLifecycleEventParser parser;

    @Autowired
    private AuctionMarketplaceViewRepository viewRepository;

    @Autowired
    private MarketplaceProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanProjection() {
        processedEventRepository.deleteAll();
        viewRepository.deleteAll();
    }

    @Test
    void projectsCreatedPublishedPartitionAndFullyFundedEvents() {
        project(event(1, AuctionLifecycleEventContract.AUCTION_CREATED, createdData()));

        AuctionMarketplaceViewEntity preliminary = view();
        assertThat(preliminary.getStatus()).isEqualTo(AuctionStatus.PENDING_VERIFICATION_RISK);
        assertThat(preliminary.isMarketplaceVisible()).isFalse();
        assertThat(preliminary.isCommerciallyComplete()).isFalse();

        project(event(2, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));
        project(event(3, AuctionLifecycleEventContract.PARTITION_ADDED,
                new PartitionAddedIntegrationEventData("partition-1", new BigDecimal("4000.00"),
                        new BigDecimal("4000.00"))));

        AuctionMarketplaceViewEntity funding = view();
        assertThat(funding.getStatus()).isEqualTo(AuctionStatus.FUNDING);
        assertThat(funding.getCurrentFunding()).isEqualByComparingTo("4000.00");
        assertThat(funding.isMarketplaceVisible()).isTrue();

        project(event(4, AuctionLifecycleEventContract.AUCTION_FULLY_FUNDED,
                new AuctionFullyFundedIntegrationEventData()));

        AuctionMarketplaceViewEntity completed = view();
        assertThat(completed.getStatus()).isEqualTo(AuctionStatus.FULLY_FUNDED);
        assertThat(completed.getCurrentFunding()).isEqualByComparingTo("9500.00");
        assertThat(completed.isMarketplaceVisible()).isFalse();
        assertThat(processedEventRepository.count()).isEqualTo(4);
    }

    @Test
    void ignoresDuplicateEventId() {
        String payload = event(2, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData());

        project(payload);
        project(payload);

        assertThat(processedEventRepository.count()).isOne();
        assertThat(viewRepository.count()).isOne();
        assertThat(view().getLastEventSequence()).isEqualTo(2);
    }

    @Test
    void defersIncrementalEventUntilPublishedArrives() {
        String partition = event(3, AuctionLifecycleEventContract.PARTITION_ADDED,
                new PartitionAddedIntegrationEventData("partition-1", new BigDecimal("2500.00"),
                        new BigDecimal("2500.00")));
        var partitionCommand = parser.parse(partition);

        projectionService.handle(partitionCommand);

        assertThat(viewRepository.findById(AUCTION_ID)).isEmpty();
        assertThat(processedEventRepository.findById(partitionCommand.eventId()))
                .get()
                .extracting(event -> event.getStatus())
                .isEqualTo(MarketplaceProcessedEventStatus.DEFERRED);

        project(event(2, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));

        assertThat(view().getStatus()).isEqualTo(AuctionStatus.FUNDING);
        assertThat(view().getCurrentFunding()).isEqualByComparingTo("2500.00");
        assertThat(processedEventRepository.findById(partitionCommand.eventId()))
                .get()
                .extracting(event -> event.getStatus())
                .isEqualTo(MarketplaceProcessedEventStatus.APPLIED);
    }

    @Test
    void olderSequenceCannotRegressPublishedView() {
        project(event(10, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));
        project(event(1, AuctionLifecycleEventContract.AUCTION_CREATED, createdData()));

        AuctionMarketplaceViewEntity projected = view();
        assertThat(projected.getStatus()).isEqualTo(AuctionStatus.PUBLISHED);
        assertThat(projected.getLastEventSequence()).isEqualTo(10);
        assertThat(projected.getTargetAmount()).isEqualByComparingTo("9500.00");
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }

    @Test
    void projectsTerminalEventsWithoutDeletingHistory() {
        project(event(1, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));
        project(event(2, AuctionLifecycleEventContract.AUCTION_EXPIRED,
                new AuctionExpiredIntegrationEventData(List.of("investment-1"))));

        assertThat(view().getStatus()).isEqualTo(AuctionStatus.EXPIRED);
        assertThat(view().getCurrentFunding()).isEqualByComparingTo("0.00");
        assertThat(view().isMarketplaceVisible()).isFalse();

        resetProjection();
        project(event(1, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));
        project(event(2, AuctionLifecycleEventContract.AUCTION_CANCELLED,
                new AuctionCancelledIntegrationEventData("MYPE request", List.of())));
        assertThat(view().getStatus()).isEqualTo(AuctionStatus.CANCELLED);

        resetProjection();
        project(event(1, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()));
        project(event(2, AuctionLifecycleEventContract.AUCTION_CLOSED,
                new AuctionClosedIntegrationEventData("closing-transaction")));
        assertThat(view().getStatus()).isEqualTo(AuctionStatus.CLOSED);
        assertThat(viewRepository.count()).isOne();
    }

    @Test
    void rebuildsTheSameProjectionFromAFullReplay() {
        List<String> lifecycle = List.of(
                event(1, AuctionLifecycleEventContract.AUCTION_CREATED, createdData()),
                event(2, AuctionLifecycleEventContract.AUCTION_PUBLISHED, publishedData()),
                event(3, AuctionLifecycleEventContract.PARTITION_ADDED,
                        new PartitionAddedIntegrationEventData("partition-1", new BigDecimal("3200.00"),
                                new BigDecimal("3200.00")))
        );
        lifecycle.forEach(this::project);
        ProjectionSnapshot first = snapshot(view());

        resetProjection();
        lifecycle.forEach(this::project);

        assertThat(snapshot(view())).isEqualTo(first);
        assertThat(processedEventRepository.count()).isEqualTo(3);
    }

    @Test
    void rejectsUnknownTypesAndVersionsBeforeWritingInbox() {
        String unknownType = rawEnvelope("UnknownEvent", 1, 1, "{}");
        String unknownVersion = rawEnvelope(AuctionLifecycleEventContract.AUCTION_CREATED, 2, 1, "{}");

        assertThatThrownBy(() -> parser.parse(unknownType)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(unknownVersion)).isInstanceOf(IllegalArgumentException.class);
        assertThat(processedEventRepository.count()).isZero();
        assertThat(viewRepository.count()).isZero();
    }

    @Test
    void rollsBackInboxWhenProjectionDataIsInvalid() {
        String invalid = rawEnvelope(
                AuctionLifecycleEventContract.AUCTION_PUBLISHED,
                1,
                1,
                "{\"invoiceId\":\"missing-required-fields\"}"
        );

        assertThatThrownBy(() -> project(invalid)).isInstanceOf(IllegalArgumentException.class);

        assertThat(processedEventRepository.count()).isZero();
        assertThat(viewRepository.count()).isZero();
    }

    private void project(String payload) {
        projectionService.handle(parser.parse(payload));
    }

    private AuctionMarketplaceViewEntity view() {
        return viewRepository.findById(AUCTION_ID).orElseThrow();
    }

    private void resetProjection() {
        processedEventRepository.deleteAll();
        viewRepository.deleteAll();
    }

    private String event(long sequence, String eventType, AuctionLifecycleEventData data) {
        try {
            return objectMapper.writeValueAsString(new AuctionLifecycleIntegrationEvent<>(
                    UUID.randomUUID().toString(), eventType, 1, AUCTION_ID, sequence, NOW.plusSeconds(sequence), data
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String rawEnvelope(String eventType, int version, long sequence, String data) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + eventType
                + "\",\"schemaVersion\":" + version + ",\"aggregateId\":\"" + AUCTION_ID
                + "\",\"sequence\":" + sequence + ",\"occurredAt\":\"" + NOW + "\",\"data\":" + data + "}";
    }

    private static AuctionCreatedIntegrationEventData createdData() {
        return new AuctionCreatedIntegrationEventData(
                "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                LocalDate.of(2026, 12, 20), new BigDecimal("10000.00"), "PEN",
                AuctionStatus.PENDING_VERIFICATION_RISK.name(), true
        );
    }

    private static AuctionPublishedIntegrationEventData publishedData() {
        return new AuctionPublishedIntegrationEventData(
                "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                LocalDate.of(2026, 12, 20), new BigDecimal("10000.00"), new BigDecimal("9800.00"),
                new BigDecimal("9500.00"), new BigDecimal("0.00"), "PEN",
                new BigDecimal("0.15"), new BigDecimal("0.04"), 97, "B", "PUBLISHED", true,
                NOW, NOW.plusSeconds(604800)
        );
    }

    private static ProjectionSnapshot snapshot(AuctionMarketplaceViewEntity view) {
        return new ProjectionSnapshot(
                view.getAuctionId(), view.getStatus(), view.getCurrentFunding(), view.getTargetAmount(),
                view.getLastEventSequence(), view.isMarketplaceVisible()
        );
    }

    private record ProjectionSnapshot(
            String auctionId,
            AuctionStatus status,
            BigDecimal currentFunding,
            BigDecimal targetAmount,
            long lastSequence,
            boolean visible
    ) {
    }

    @TestConfiguration
    static class ProjectionTestConfiguration {

        @Bean
        Clock projectionClock() {
            return Clock.fixed(NOW.plusSeconds(1000), ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper projectionObjectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
