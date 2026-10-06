package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCancelledEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionClosedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionExpiredEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionFullyFundedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionPublishedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.PartitionAddedEvent;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleIntegrationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

@Component
public class InvestmentOutboxEventListener {

    private static final String BINDING_NAME = "auctionLifecycle-out-0";

    private final OutboxEventRepository repository;
    private final AuctionIntegrationEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InvestmentOutboxEventListener(
            OutboxEventRepository repository,
            AuctionIntegrationEventMapper mapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionCreatedEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionPublishedEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(PartitionAddedEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionFullyFundedEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionExpiredEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionCancelledEvent event) { capture(event); }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(AuctionClosedEvent event) { capture(event); }

    private void capture(Object domainEvent) {
        MappedAuctionIntegrationEvent mapped = mapper.map(domainEvent);
        String eventId = UUID.randomUUID().toString();
        OutboxEvent outbox = repository.save(new OutboxEvent(
                eventId,
                mapped.aggregateId(),
                mapped.eventType(),
                AuctionLifecycleEventContract.SCHEMA_VERSION,
                BINDING_NAME,
                clock.instant()
        ));
        if (outbox.getSequence() == null) {
            throw new IllegalStateException("Outbox sequence was not assigned");
        }
        var envelope = new AuctionLifecycleIntegrationEvent<>(
                eventId,
                mapped.eventType(),
                AuctionLifecycleEventContract.SCHEMA_VERSION,
                mapped.aggregateId(),
                outbox.getSequence(),
                mapped.occurredAt(),
                mapped.data()
        );
        try {
            outbox.attachPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Auction integration event could not be serialized", exception);
        }
    }
}
