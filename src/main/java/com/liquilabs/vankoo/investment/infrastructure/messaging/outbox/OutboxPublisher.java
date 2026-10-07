package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

@Component
public class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final StreamBridge streamBridge;
    private final Clock clock;
    private final int batchSize;
    private final Duration publishedRetention;

    public OutboxPublisher(
            OutboxEventRepository repository,
            StreamBridge streamBridge,
            Clock clock,
            @Value("${vankoo.investment.outbox.batch-size:50}") int batchSize,
            @Value("${vankoo.investment.outbox.published-retention:P7D}") Duration publishedRetention
    ) {
        this.repository = repository;
        this.streamBridge = streamBridge;
        this.clock = clock;
        this.batchSize = batchSize;
        this.publishedRetention = publishedRetention;
    }

    @Scheduled(fixedDelayString = "${vankoo.investment.outbox.publish-delay:PT1S}")
    @Transactional
    public void publishPending() {
        var now = clock.instant();
        var events = repository.findPublishable(
                OutboxStatus.PENDING,
                now,
                PageRequest.of(0, batchSize)
        );
        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    @Scheduled(fixedDelayString = "${vankoo.investment.outbox.cleanup-delay:PT1H}")
    @Transactional
    public void cleanupPublished() {
        long deleted = repository.deleteByStatusAndPublishedAtBefore(
                OutboxStatus.PUBLISHED,
                clock.instant().minus(publishedRetention)
        );
        if (deleted > 0) {
            LOGGER.info("Deleted {} published Auction outbox event(s)", deleted);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            boolean accepted = streamBridge.send(event.getBindingName(), MessageBuilder
                    .withPayload(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setHeader(KafkaHeaders.KEY, event.getAggregateId())
                    .setHeader("eventId", event.getEventId())
                    .setHeader("eventType", event.getEventType())
                    .setHeader("schemaVersion", event.getSchemaVersion())
                    .setHeader("sequence", event.getSequence())
                    .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE)
                    .build());
            if (!accepted) {
                throw new IllegalStateException("Auction lifecycle binding rejected the event");
            }
            event.markPublished(clock.instant());
        } catch (RuntimeException exception) {
            event.markFailed(exception.getMessage(), clock.instant());
            LOGGER.error("Failed to publish Auction outbox event {} ({})",
                    event.getEventId(), event.getEventType(), exception);
        }
    }
}
