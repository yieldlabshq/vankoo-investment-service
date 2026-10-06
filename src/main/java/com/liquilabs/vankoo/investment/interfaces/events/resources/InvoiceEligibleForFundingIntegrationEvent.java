package com.liquilabs.vankoo.investment.interfaces.events.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record InvoiceEligibleForFundingIntegrationEvent(
        @JsonProperty("EventId") String eventId,
        @JsonProperty("OccurredOn") String occurredOn,
        @JsonProperty("InvoiceId") String invoiceId,
        @JsonProperty("MypeId") String mypeId,
        @JsonProperty("PayerRuc") String payerRuc,
        @JsonProperty("PayerName") String payerName,
        @JsonProperty("DueDate") String dueDate,
        @JsonProperty("Currency") String currency,
        @JsonProperty("TotalAmount") BigDecimal totalAmount
) {}