package com.liquilabs.vankoo.investment.domain.model.queries;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;

import java.util.Objects;
import java.util.Set;

public record GetMarketplaceAuctionsQuery(
        Set<AuctionStatus> statuses,
        Currency currency,
        Boolean greenCertified,
        int page,
        int size,
        MarketplaceSortField sortField,
        MarketplaceSortDirection sortDirection
) {
    public GetMarketplaceAuctionsQuery {
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses are required"));
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("At least one marketplace status is required");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        Objects.requireNonNull(sortField, "sortField is required");
        Objects.requireNonNull(sortDirection, "sortDirection is required");
    }
}
