package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");

    @Test
    void marksPublishedOnlyAfterTheBindingAcceptsTheStableEnvelope() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        StreamBridge streamBridge = mock(StreamBridge.class);
        OutboxEvent event = event();
        when(repository.findPublishable(eq(OutboxStatus.PENDING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(streamBridge.send(eq("auctionLifecycle-out-0"), any(Message.class))).thenReturn(true);

        publisher(repository, streamBridge).publishPending();

        ArgumentCaptor<Message<?>> message = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("auctionLifecycle-out-0"), message.capture());
        assertThat(message.getValue().getHeaders().get(KafkaHeaders.KEY)).isEqualTo("auction-1");
        assertThat(message.getValue().getHeaders().get("eventId")).isEqualTo("event-1");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void retainsTheSameEventIdAndSchedulesBackoffWhenPublicationFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        StreamBridge streamBridge = mock(StreamBridge.class);
        OutboxEvent event = event();
        when(repository.findPublishable(eq(OutboxStatus.PENDING), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(streamBridge.send(anyString(), any(Message.class))).thenReturn(false);

        publisher(repository, streamBridge).publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getEventId()).isEqualTo("event-1");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(event.getLastError()).contains("rejected");
    }

    @Test
    void removesOnlyPublishedEventsOlderThanTheRetentionWindow() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        StreamBridge streamBridge = mock(StreamBridge.class);
        when(repository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, NOW.minus(Duration.ofDays(7))))
                .thenReturn(3L);

        publisher(repository, streamBridge).cleanupPublished();

        verify(repository).deleteByStatusAndPublishedAtBefore(
                OutboxStatus.PUBLISHED,
                NOW.minus(Duration.ofDays(7))
        );
    }

    private static OutboxPublisher publisher(OutboxEventRepository repository, StreamBridge streamBridge) {
        return new OutboxPublisher(
                repository,
                streamBridge,
                Clock.fixed(NOW, ZoneOffset.UTC),
                10,
                Duration.ofDays(7)
        );
    }

    private static OutboxEvent event() {
        return new OutboxEvent(
                1L,
                "event-1",
                "auction-1",
                "AuctionCreated",
                1,
                "auctionLifecycle-out-0",
                "{\"eventId\":\"event-1\"}",
                NOW
        );
    }
}
