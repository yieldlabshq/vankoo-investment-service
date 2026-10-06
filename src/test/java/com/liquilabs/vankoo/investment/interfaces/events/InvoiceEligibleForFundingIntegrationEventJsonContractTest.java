package com.liquilabs.vankoo.investment.interfaces.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.interfaces.events.resources.InvoiceEligibleForFundingIntegrationEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEligibleForFundingIntegrationEventJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesThePascalCaseJsonPublishedByInvoicing() throws JsonProcessingException {
        String json = """
                {
                  "EventId": "10000000-0000-4000-8000-000000000010",
                  "OccurredOn": "2026-09-14T15:00:00Z",
                  "InvoiceId": "invoice-1",
                  "MypeId": "mype-1",
                  "PayerRuc": "20123456789",
                  "PayerName": "Pagador S.A.",
                  "DueDate": "2026-10-14T00:00:00Z",
                  "Currency": "PEN",
                  "TotalAmount": 10000.00
                }
                """;

        InvoiceEligibleForFundingIntegrationEvent event = objectMapper.readValue(
                json,
                InvoiceEligibleForFundingIntegrationEvent.class
        );

        assertThat(event.eventId()).isEqualTo("10000000-0000-4000-8000-000000000010");
        assertThat(event.occurredOn()).isEqualTo("2026-09-14T15:00:00Z");
        assertThat(event.invoiceId()).isEqualTo("invoice-1");
        assertThat(event.mypeId()).isEqualTo("mype-1");
        assertThat(event.payerRuc()).isEqualTo("20123456789");
        assertThat(event.payerName()).isEqualTo("Pagador S.A.");
        assertThat(event.dueDate()).isEqualTo("2026-10-14T00:00:00Z");
        assertThat(event.currency()).isEqualTo("PEN");
        assertThat(event.totalAmount()).isEqualByComparingTo("10000.00");
    }
}
