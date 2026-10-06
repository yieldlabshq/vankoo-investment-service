package com.liquilabs.vankoo.investment.domain.model.queries;

public enum MarketplaceSortField {
    EXPIRES_AT("expiresAt"),
    DUE_DATE("dueDate"),
    PUBLISHED_AT("publishedAt"),
    TARGET_AMOUNT("targetAmount");

    private final String property;

    MarketplaceSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
