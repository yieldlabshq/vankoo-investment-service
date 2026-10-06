package com.liquilabs.vankoo.investment.application.internal.scheduling;

import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuctionExpirationScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionExpirationScheduler.class);

    private final AuctionCommandService auctionCommandService;

    public AuctionExpirationScheduler(AuctionCommandService auctionCommandService) {
        this.auctionCommandService = auctionCommandService;
    }

    @Scheduled(fixedDelayString = "${vankoo.investment.lifecycle.expiration-check-delay:PT1M}")
    public void expireAuctions() {
        int quotes = auctionCommandService.expireDueQuotes();
        int expired = auctionCommandService.expireDueAuctions();
        if (quotes > 0) {
            LOGGER.info("Expired {} stale financial quote(s)", quotes);
        }
        if (expired > 0) {
            LOGGER.info("Expired {} auction(s) whose funding windows elapsed", expired);
        }
    }
}
