package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import com.liquilabs.vankoo.investment.domain.model.queries.AuctionMarketplacePage;
import com.liquilabs.vankoo.investment.domain.model.queries.AuctionMarketplaceView;

import java.util.List;

public record MarketplacePageResource(
        List<AuctionMarketplaceView> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {
    public static MarketplacePageResource from(AuctionMarketplacePage page) {
        return new MarketplacePageResource(
                page.content(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.sort()
        );
    }
}
