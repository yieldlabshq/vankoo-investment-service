package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Embeddable
public record Percentage(BigDecimal value) {
    public Percentage {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Percentage must be positive");
        }
    }

    public Money of(Money money) {
        BigDecimal calculatedAmount = money.amount()
                .multiply(this.value)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new Money(calculatedAmount, money.currency());
    }
}