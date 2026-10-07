package com.liquilabs.vankoo.investment.interfaces.events.transform;

import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.AuctionLifecycleEventParser;
import org.springframework.stereotype.Component;

@Component
public class AuctionLifecycleEventToCommandAssembler {

    private final AuctionLifecycleEventParser eventParser;

    public AuctionLifecycleEventToCommandAssembler(AuctionLifecycleEventParser eventParser) {
        this.eventParser = eventParser;
    }

    public ProjectAuctionLifecycleEventCommand toCommand(byte[] payload) {
        return eventParser.parse(payload);
    }
}
