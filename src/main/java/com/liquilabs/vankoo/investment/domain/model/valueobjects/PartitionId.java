package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public record PartitionId(String uuid) {
    public PartitionId() { this(UUID.randomUUID().toString()); }
    public PartitionId {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("PartitionId cannot be null/blank");
    }
}
