package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Embeddable
public record Money(
        BigDecimal amount,
        @Enumerated(EnumType.STRING) Currency currency
) {
    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative or null");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Money supports at most two decimal places", e);
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount()), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount()), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), this.currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount()) > 0;
    }
    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount()) < 0;
    }

    private void requireSameCurrency(Money other) {
        if (this.currency != other.currency()) {
            throw new IllegalArgumentException("Currencies must match for operations");
        }
    }
}
