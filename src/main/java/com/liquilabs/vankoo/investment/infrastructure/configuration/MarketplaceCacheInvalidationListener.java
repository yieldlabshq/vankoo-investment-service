package com.liquilabs.vankoo.investment.infrastructure.configuration;

import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.MarketplaceProjectionUpdatedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MarketplaceCacheInvalidationListener {

    @CacheEvict(cacheNames = MarketplaceCacheConfiguration.MARKETPLACE_CACHE, allEntries = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MarketplaceProjectionUpdatedEvent event) {
        // Cache eviction is applied by the interceptor after the projection transaction commits.
    }
}
