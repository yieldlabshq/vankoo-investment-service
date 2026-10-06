package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record InvoiceId(String uuid) {
    public InvoiceId() {
        this(UUID.randomUUID().toString());
    }
    public InvoiceId {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("InvoiceId cannot be null or blank");
        }
    }
}
