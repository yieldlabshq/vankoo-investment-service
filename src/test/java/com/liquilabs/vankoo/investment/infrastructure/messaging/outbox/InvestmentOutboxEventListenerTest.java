package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionPublishedEvent;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentOutboxEventListenerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void storesTheVersionedPublishedEnvelopeWithTheDatabaseSequence() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "sequence", 42L);
            return event;
        });
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var listener = new InvestmentOutboxEventListener(
                repository,
                new AuctionIntegrationEventMapper(),
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        listener.on(new AuctionPublishedEvent(
                "auction-1", "invoice-1", "mype-1", "20123456789", "Pagador S.A.",
                LocalDate.of(2026, 11, 14), new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                new BigDecimal("9884.00"), new BigDecimal("0.00"), "PEN", new BigDecimal("0.15"),
                new BigDecimal("0.011735"), 30, ScoreGrade.B, AuctionStatus.PUBLISHED, true,
                NOW, NOW.plusSeconds(604800)
        ));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        var json = objectMapper.readTree(saved.getPayload());

        assertThat(saved.getBindingName()).isEqualTo("auctionLifecycle-out-0");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(json.path("eventId").asText()).isEqualTo(saved.getEventId());
        assertThat(json.path("eventType").asText()).isEqualTo("AuctionPublished");
        assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.path("aggregateId").asText()).isEqualTo("auction-1");
        assertThat(json.path("sequence").asLong()).isEqualTo(42L);
        assertThat(json.path("data").path("targetAmount").decimalValue())
                .isEqualByComparingTo("9884.00");
    }
}
