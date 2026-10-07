package com.liquilabs.vankoo.investment.interfaces.events.consumers;

import com.liquilabs.vankoo.investment.domain.services.AuctionMarketplaceProjectionService;
import com.liquilabs.vankoo.investment.interfaces.events.transform.AuctionLifecycleEventToCommandAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

@Configuration
public class AuctionMarketplaceEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionMarketplaceEventConsumer.class);

    private final AuctionLifecycleEventToCommandAssembler assembler;
    private final AuctionMarketplaceProjectionService projectionService;

    public AuctionMarketplaceEventConsumer(
            AuctionLifecycleEventToCommandAssembler assembler,
            AuctionMarketplaceProjectionService projectionService
    ) {
        this.assembler = assembler;
        this.projectionService = projectionService;
    }

    @Bean
    public Consumer<Message<byte[]>> projectAuctionMarketplace() {
        return message -> {
            var command = assembler.toCommand(message.getPayload());
            LOGGER.info(
                    "Projecting auction lifecycle event. eventId={}, eventType={}, auctionId={}, sequence={}",
                    command.eventId(), command.eventType(), command.aggregateId(), command.sequence()
            );
            projectionService.handle(command);
        };
    }
}
