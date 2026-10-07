package com.liquilabs.vankoo.investment.interfaces.events.consumers;

import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.interfaces.events.resources.InvoiceEligibleForFundingIntegrationEvent;
import com.liquilabs.vankoo.investment.interfaces.events.transform.InvoicingOcrEventToCommandAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
public class InvoicingOcrEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvoicingOcrEventConsumer.class);
    private final AuctionCommandService auctionCommandService;

    public InvoicingOcrEventConsumer(AuctionCommandService auctionCommandService) {
        this.auctionCommandService = auctionCommandService;
    }

    @Bean
    public Consumer<InvoiceEligibleForFundingIntegrationEvent> processInvoiceEligibleForFunding() {
        return event -> {
            LOGGER.info(
                    "Factura elegible para financiamiento recibida desde Invoicing. Factura ID: {}",
                    event.invoiceId());

            try {
                var command = InvoicingOcrEventToCommandAssembler.toCommandFromEvent(event);
                var auctionId = auctionCommandService.handle(command);
                LOGGER.info("[Kafka] Subasta creada o ya existente. ID: {}", auctionId.uuid());
            } catch (IllegalArgumentException e) {
                LOGGER.error("[Kafka] Evento inválido, se descarta. invoiceId={}, motivo: {}",
                        event.invoiceId(), e.getMessage());
            } catch (Exception e) {
                LOGGER.error("[Kafka] Error inesperado al procesar el evento de Invoicing (se reintentará): {}",
                        e.getMessage(), e);
                throw e;
            }
        };
    }
}
