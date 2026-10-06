package com.liquilabs.vankoo.investment.interfaces.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuctionLifecycleIntegrationEventSerializationTest {

    private static final String AUCTION_ID = "20000000-0000-4000-8000-000000000001";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-14T16:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesTheVersionedEnvelopeForEveryLifecycleEvent() throws JsonProcessingException {
        List<AuctionLifecycleIntegrationEvent<? extends AuctionLifecycleEventData>> events = List.of(
                event("10000000-0000-4000-8000-000000000001", AuctionLifecycleEventContract.AUCTION_CREATED, 1,
                        new AuctionCreatedIntegrationEventData(
                                "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                                LocalDate.of(2026, 10, 14), new BigDecimal("10000.00"), "PEN",
                                "PENDING_VERIFICATION_RISK", true
                        )),
                event("10000000-0000-4000-8000-000000000002", AuctionLifecycleEventContract.AUCTION_PUBLISHED, 2,
                        publishedData()),
                event("10000000-0000-4000-8000-000000000003", AuctionLifecycleEventContract.PARTITION_ADDED, 3,
                        new PartitionAddedIntegrationEventData(
                                "partition-1", new BigDecimal("4000.00"), new BigDecimal("4000.00")
                        )),
                event("10000000-0000-4000-8000-000000000004", AuctionLifecycleEventContract.AUCTION_FULLY_FUNDED, 4,
                        new AuctionFullyFundedIntegrationEventData()),
                event("10000000-0000-4000-8000-000000000005", AuctionLifecycleEventContract.AUCTION_EXPIRED, 5,
                        new AuctionExpiredIntegrationEventData(List.of("investment-tx-1"))),
                event("10000000-0000-4000-8000-000000000006", AuctionLifecycleEventContract.AUCTION_CANCELLED, 6,
                        new AuctionCancelledIntegrationEventData("Invoice disputed", List.of("investment-tx-2"))),
                event("10000000-0000-4000-8000-000000000007", AuctionLifecycleEventContract.AUCTION_CLOSED, 7,
                        new AuctionClosedIntegrationEventData("settlement-tx-1"))
        );

        for (AuctionLifecycleIntegrationEvent<? extends AuctionLifecycleEventData> event : events) {
            JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

            assertThat(json.size()).isEqualTo(7);
            assertThat(json.path("eventId").asText()).isEqualTo(event.eventId());
            assertThat(json.path("eventType").asText()).isEqualTo(event.eventType());
            assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(json.path("aggregateId").asText()).isEqualTo(AUCTION_ID);
            assertThat(json.path("sequence").asLong()).isEqualTo(event.sequence());
            assertThat(json.path("occurredAt").asText()).isEqualTo("2026-09-14T16:00:00Z");
            assertThat(json.path("data").isObject()).isTrue();
        }
    }

    @Test
    void roundTripsTheSelfContainedAuctionPublishedPayloadWithoutDomainTypes() throws JsonProcessingException {
        var original = event(
                "10000000-0000-4000-8000-000000000002",
                AuctionLifecycleEventContract.AUCTION_PUBLISHED,
                2,
                publishedData()
        );

        String json = objectMapper.writeValueAsString(original);
        AuctionLifecycleIntegrationEvent<AuctionPublishedIntegrationEventData> restored = objectMapper.readValue(
                json,
                new TypeReference<>() {
                }
        );

        assertThat(restored).isEqualTo(original);
        assertThat(restored.data().currency()).isEqualTo("PEN");
        assertThat(restored.data().riskGrade()).isEqualTo("B");
        assertThat(restored.data().status()).isEqualTo("PUBLISHED");
        assertThat(restored.data().targetAmount()).isEqualByComparingTo("9884.00");
        assertThat(restored.data().investorTea()).isEqualByComparingTo("0.15");
        assertThat(restored.data().publishedAt()).isEqualTo(OCCURRED_AT);
    }

    private static AuctionPublishedIntegrationEventData publishedData() {
        return new AuctionPublishedIntegrationEventData(
                "invoice-1",
                "mype-1",
                "20123456789",
                "Pagador S.A.",
                LocalDate.of(2026, 10, 14),
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("9884.00"),
                new BigDecimal("0.00"),
                "PEN",
                new BigDecimal("0.15"),
                new BigDecimal("0.011735"),
                30,
                "B",
                "PUBLISHED",
                true,
                OCCURRED_AT,
                Instant.parse("2026-09-21T16:00:00Z")
        );
    }

    private static <T extends AuctionLifecycleEventData> AuctionLifecycleIntegrationEvent<T> event(
            String eventId,
            String eventType,
            long sequence,
            T data
    ) {
        return new AuctionLifecycleIntegrationEvent<>(
                eventId,
                eventType,
                AuctionLifecycleEventContract.SCHEMA_VERSION,
                AUCTION_ID,
                sequence,
                OCCURRED_AT,
                data
        );
    }
}
