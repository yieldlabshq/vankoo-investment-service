package com.liquilabs.vankoo.investment.domain.model.events;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AuctionCreatedEvent(
        String auctionId,
        String invoiceId,
        String mypeId,

        String payerRuc,
        String payerName,
        LocalDate dueDate,

        BigDecimal invoiceAmount,
        String currency,
        AuctionStatus status,
        boolean greenCertified,
        Instant occurredAt
) {
}
