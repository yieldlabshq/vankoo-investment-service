package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;

@Embeddable
public record InvestorParticipation(
        @Embedded UserId investorId,

        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "amount", column = @Column(name = "participation_amount")),
                @AttributeOverride(name = "currency", column = @Column(name = "participation_currency"))
        })
        Money amount,

        @Embedded Percentage percentage,
        String investorName,
        String investorRUC,
        String investorEmail
) {
    public InvestorParticipation {
        if (investorId == null) throw new IllegalArgumentException("InvestorId required");
        if (amount == null) throw new IllegalArgumentException("Amount required");
    }
}