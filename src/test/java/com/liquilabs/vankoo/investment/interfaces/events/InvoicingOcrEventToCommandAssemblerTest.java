package com.liquilabs.vankoo.investment.interfaces.events;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.interfaces.events.resources.InvoiceEligibleForFundingIntegrationEvent;
import com.liquilabs.vankoo.investment.interfaces.events.transform.InvoicingOcrEventToCommandAssembler;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicingOcrEventToCommandAssemblerTest {

    @Test
    void preservesTotalAmountAsThePilotFundableCandidateAndParsesUtcDueDate() {
        var event = new InvoiceEligibleForFundingIntegrationEvent(
                "event-1",
                "2026-09-07T17:00:00Z",
                "invoice-1",
                "mype-1",
                "20123456789",
                "Pagador S.A.",
                "2026-11-06T00:00:00Z",
                "pen",
                new BigDecimal("10000.00")
        );

        var command = InvoicingOcrEventToCommandAssembler.toCommandFromEvent(event);

        assertThat(command.invoiceAmount().amount()).isEqualByComparingTo("10000.00");
        assertThat(command.invoiceAmount().currency()).isEqualTo(Currency.PEN);
        assertThat(command.dueDate()).isEqualTo(LocalDate.of(2026, 11, 6));
    }
}
