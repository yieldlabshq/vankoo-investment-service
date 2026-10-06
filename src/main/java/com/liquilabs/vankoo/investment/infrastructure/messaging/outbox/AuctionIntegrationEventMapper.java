package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import com.liquilabs.vankoo.investment.domain.model.events.AuctionCancelledEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionClosedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionExpiredEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionFullyFundedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionPublishedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.PartitionAddedEvent;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionCancelledIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionClosedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionCreatedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionExpiredIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionFullyFundedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionPublishedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.events.resources.PartitionAddedIntegrationEventData;
import org.springframework.stereotype.Component;

@Component
public class AuctionIntegrationEventMapper {

    public MappedAuctionIntegrationEvent map(Object event) {
        if (event instanceof AuctionCreatedEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_CREATED, value.occurredAt(),
                    new AuctionCreatedIntegrationEventData(
                            value.invoiceId(), value.mypeId(), value.payerRuc(), value.payerName(), value.dueDate(),
                            value.invoiceAmount(), value.currency(), value.status().name(), value.greenCertified()
                    )
            );
        }
        if (event instanceof AuctionPublishedEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_PUBLISHED, value.publishedAt(),
                    new AuctionPublishedIntegrationEventData(
                            value.invoiceId(), value.mypeId(), value.payerRuc(), value.payerName(), value.dueDate(),
                            value.invoiceAmount(), value.fundableAmount(), value.targetAmount(), value.currentFunding(),
                            value.currency(), value.investorTea(), value.investorTermRate(), value.quotedTermDays(),
                            value.riskGrade().name(), value.status().name(), value.greenCertified(),
                            value.publishedAt(), value.expiresAt()
                    )
            );
        }
        if (event instanceof PartitionAddedEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.PARTITION_ADDED, value.occurredAt(),
                    new PartitionAddedIntegrationEventData(
                            value.partitionId(), value.addedAmount(), value.newCurrentFunding()
                    )
            );
        }
        if (event instanceof AuctionFullyFundedEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_FULLY_FUNDED, value.occurredAt(),
                    new AuctionFullyFundedIntegrationEventData()
            );
        }
        if (event instanceof AuctionExpiredEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_EXPIRED, value.occurredAt(),
                    new AuctionExpiredIntegrationEventData(value.investmentTransactionIds())
            );
        }
        if (event instanceof AuctionCancelledEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_CANCELLED, value.occurredAt(),
                    new AuctionCancelledIntegrationEventData(value.reason(), value.investmentTransactionIds())
            );
        }
        if (event instanceof AuctionClosedEvent value) {
            return new MappedAuctionIntegrationEvent(
                    value.auctionId(), AuctionLifecycleEventContract.AUCTION_CLOSED, value.occurredAt(),
                    new AuctionClosedIntegrationEventData(value.closingTransactionId())
            );
        }
        throw new IllegalArgumentException("Unsupported Auction domain event: " + event.getClass().getName());
    }
}
