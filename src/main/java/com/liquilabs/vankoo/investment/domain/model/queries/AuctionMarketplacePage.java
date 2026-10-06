package com.liquilabs.vankoo.investment.domain.model.queries;

import java.util.List;

public record AuctionMarketplacePage(
        List<AuctionMarketplaceView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {
    public AuctionMarketplacePage {
        content = List.copyOf(content);
    }
}
