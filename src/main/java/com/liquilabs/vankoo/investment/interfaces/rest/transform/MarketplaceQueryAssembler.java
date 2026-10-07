package com.liquilabs.vankoo.investment.interfaces.rest.transform;

import com.liquilabs.vankoo.investment.domain.model.queries.GetMarketplaceAuctionsQuery;
import com.liquilabs.vankoo.investment.domain.model.queries.MarketplaceSortDirection;
import com.liquilabs.vankoo.investment.domain.model.queries.MarketplaceSortField;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarketplaceQueryAssembler {

    private static final Set<AuctionStatus> DEFAULT_STATUSES = Set.of(
            AuctionStatus.PUBLISHED,
            AuctionStatus.FUNDING
    );
    private static final Set<AuctionStatus> PUBLIC_FILTER_STATUSES = EnumSet.of(
            AuctionStatus.PUBLISHED,
            AuctionStatus.FUNDING,
            AuctionStatus.FULLY_FUNDED,
            AuctionStatus.EXPIRED,
            AuctionStatus.CANCELLED,
            AuctionStatus.CLOSED
    );

    private MarketplaceQueryAssembler() {
    }

    public static GetMarketplaceAuctionsQuery toQuery(
            List<String> statusValues,
            String currencyValue,
            Boolean greenCertified,
            int page,
            int size,
            String sortValue
    ) {
        return new GetMarketplaceAuctionsQuery(
                parseStatuses(statusValues),
                parseCurrency(currencyValue),
                greenCertified,
                page,
                size,
                parseSortField(sortValue),
                parseSortDirection(sortValue)
        );
    }

    private static Set<AuctionStatus> parseStatuses(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_STATUSES;
        }
        var statuses = new LinkedHashSet<AuctionStatus>();
        for (String value : values) {
            AuctionStatus status;
            try {
                status = AuctionStatus.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unsupported marketplace status: " + value, exception);
            }
            if (!PUBLIC_FILTER_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Status is not public in Marketplace: " + value);
            }
            statuses.add(status);
        }
        return statuses;
    }

    private static Currency parseCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Currency.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported marketplace currency: " + value, exception);
        }
    }

    private static MarketplaceSortField parseSortField(String value) {
        String[] parts = sortParts(value);
        for (MarketplaceSortField field : MarketplaceSortField.values()) {
            if (field.property().equals(parts[0])) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unsupported marketplace sort field: " + parts[0]);
    }

    private static MarketplaceSortDirection parseSortDirection(String value) {
        String[] parts = sortParts(value);
        try {
            return MarketplaceSortDirection.valueOf(parts[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported marketplace sort direction: " + parts[1], exception);
        }
    }

    private static String[] sortParts(String value) {
        if (value == null || value.isBlank()) {
            return new String[]{"expiresAt", "asc"};
        }
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("sort must have the format field,direction");
        }
        return parts;
    }
}
