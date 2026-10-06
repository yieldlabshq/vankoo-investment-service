package com.liquilabs.vankoo.investment.interfaces.events;

import com.liquilabs.vankoo.investment.domain.model.commands.AuctionLifecycleProjectionEventType;
import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;
import com.liquilabs.vankoo.investment.domain.services.AuctionMarketplaceProjectionService;
import com.liquilabs.vankoo.investment.interfaces.events.consumers.AuctionMarketplaceEventConsumer;
import com.liquilabs.vankoo.investment.interfaces.events.transform.AuctionLifecycleEventToCommandAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuctionMarketplaceEventConsumerTest {

    @Test
    void transformsTheMessageAndDelegatesProjection() {
        var assembler = mock(AuctionLifecycleEventToCommandAssembler.class);
        var projectionService = mock(AuctionMarketplaceProjectionService.class);
        var consumer = new AuctionMarketplaceEventConsumer(assembler, projectionService);
        byte[] payload = "{}".getBytes();
        var command = new ProjectAuctionLifecycleEventCommand(
                "event-1",
                AuctionLifecycleProjectionEventType.AuctionCreated,
                1,
                "auction-1",
                1,
                Instant.parse("2026-09-14T20:00:00Z"),
                Map.of(),
                "{}"
        );
        when(assembler.toCommand(payload)).thenReturn(command);

        consumer.projectAuctionMarketplace().accept(MessageBuilder.withPayload(payload).build());

        verify(assembler).toCommand(payload);
        verify(projectionService).handle(command);
    }
}
