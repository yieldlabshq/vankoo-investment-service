package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketplaceProcessedEventRepository extends JpaRepository<MarketplaceProcessedEvent, String> {

    List<MarketplaceProcessedEvent> findByAggregateIdAndStatusOrderBySequenceAsc(
            String aggregateId,
            MarketplaceProcessedEventStatus status
    );
}
