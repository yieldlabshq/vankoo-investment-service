package com.liquilabs.vankoo.investment.interfaces.events.resources;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AuctionCreatedIntegrationEventData(
        String invoiceId,
        String mypeId,
        String payerRuc,
        String payerName,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate dueDate,
        BigDecimal invoiceAmount,
        String currency,
        String status,
        boolean greenCertified
) implements AuctionLifecycleEventData {
}
