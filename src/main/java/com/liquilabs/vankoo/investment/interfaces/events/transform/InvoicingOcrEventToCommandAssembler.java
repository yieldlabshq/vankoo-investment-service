package com.liquilabs.vankoo.investment.interfaces.events.transform;

import com.liquilabs.vankoo.investment.domain.model.commands.CreateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.interfaces.events.resources.InvoiceEligibleForFundingIntegrationEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class InvoicingOcrEventToCommandAssembler {

    public static CreateAuctionCommand toCommandFromEvent(InvoiceEligibleForFundingIntegrationEvent event) {
        return new CreateAuctionCommand(
                new InvoiceId(requireNotBlank(event.invoiceId(), "InvoiceId")),
                new UserId(requireNotBlank(event.mypeId(), "MypeId")),
                new Money(requireNotNull(event.totalAmount(), "TotalAmount"), parseCurrency(event.currency())),
                false,
                event.payerRuc(),
                event.payerName(),
                parseDateTime(event.dueDate(), "DueDate")
        );
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " no puede ser nulo o vacío");
        }
        return value.trim();
    }

    private static BigDecimal requireNotNull(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " no puede ser nulo");
        }
        return value;
    }

    private static Currency parseCurrency(String raw) {
        String currency = requireNotBlank(raw, "Currency");
        try {
            return Currency.valueOf(currency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Currency inválida: " + raw);
        }
    }

    private static LocalDate parseDateTime(String raw, String field) {
        String value = requireNotBlank(raw, field);
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
        } catch (DateTimeParseException ignored) {
            try {
                return java.time.LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDate();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDate.parse(value, DateTimeFormatter.ISO_DATE);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException(field + " con formato inválido: " + raw, e);
                }
            }
        }
    }
}
