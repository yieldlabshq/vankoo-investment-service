package com.liquilabs.vankoo.investment.interfaces.events.resources;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AuctionPublishedIntegrationEventData(
        String invoiceId,
        String mypeId,
        String payerRuc,
        String payerName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dueDate,
        BigDecimal invoiceAmount,
        BigDecimal fundableAmount,
        BigDecimal targetAmount,
        BigDecimal currentFunding,
        String currency,
        BigDecimal investorTea,
        BigDecimal investorTermRate,
        int quotedTermDays,
        String riskGrade,
        String status,
        boolean greenCertified,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant publishedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant expiresAt
) implements AuctionLifecycleEventData {
}
