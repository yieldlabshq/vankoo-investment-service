package com.liquilabs.vankoo.investment.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liquilabs.vankoo.investment.domain.model.commands.*;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionFullyFundedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.PartitionAddedEvent;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAllActiveAuctionsQuery;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAuctionByIdQuery;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.domain.services.AuctionMarketplaceProjectionService;
import com.liquilabs.vankoo.investment.domain.services.AuctionQueryService;
import com.liquilabs.vankoo.investment.infrastructure.messaging.outbox.OutboxEventRepository;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.AuctionLifecycleEventParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.cloud.stream.default-binder=test")
@AutoConfigureMockMvc
@Import({AuctionFinancialFlowIntegrationTest.FixedClockConfiguration.class, TestChannelBinderConfiguration.class})
@ActiveProfiles("test")
@RecordApplicationEvents
class AuctionFinancialFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuctionCommandService auctionCommandService;

    @Autowired
    private AuctionQueryService auctionQueryService;

    @Autowired
    private AuctionMarketplaceProjectionService projectionService;

    @Autowired
    private AuctionLifecycleEventParser eventParser;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void runsTheEvaluationQuoteAcceptanceAndInvestmentFlow() throws Exception {
        String auctionId = mockMvc.perform(post("/api/v1/auctions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": "invoice-http-flow",
                                  "mypeId": "mype-http-flow",
                                  "invoiceAmount": 10000.00,
                                  "currency": "PEN",
                                  "greenCertified": false,
                                  "payerRuc": "20123456789",
                                  "payerName": "Pagador HTTP S.A.",
                                  "dueDate": "2026-11-06"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(put("/api/v1/internal/auctions/{auctionId}/evaluation", auctionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assessmentId": "assessment-http-flow",
                                  "riskGrade": "B",
                                  "fullBalanceOutstanding": true,
                                  "assessedAt": "2026-09-07T17:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.fullBalanceOutstandingConfirmed").value(true));

        String quoteJson = mockMvc.perform(post("/api/v1/auctions/{auctionId}/quotes", auctionId)
                        .header("X-User-Id", "mype-http-flow"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.investorTeaPct").value(15.000000))
                .andExpect(jsonPath("$.fundingTarget").value(9769.76))
                .andExpect(jsonPath("$.platformFeeBase").value(60.00))
                .andExpect(jsonPath("$.platformFeeTax").value(10.80))
                .andExpect(jsonPath("$.mypeAdvance").value(9698.96))
                .andReturn().getResponse().getContentAsString();
        JsonNode quote = objectMapper.readTree(quoteJson);
        String quoteId = quote.get("quoteId").asText();

        mockMvc.perform(post("/api/v1/auctions/{auctionId}/quotes/{quoteId}/accept", auctionId, quoteId)
                        .header("X-User-Id", "mype-http-flow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.acceptedQuote.quoteId").value(quoteId));

        String investmentJson = mockMvc.perform(post("/api/v1/auctions/{auctionId}/investments", auctionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "investorId": "investor-http-flow",
                                  "amount": 500.00,
                                  "currency": "PEN",
                                  "transactionId": "transaction-http-flow"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.expectedGrossProfit").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(investmentJson).has("returnRate")).isFalse();

        outboxEventRepository.findByAggregateIdOrderBySequence(auctionId).stream()
                .map(event -> eventParser.parse(event.getPayload()))
                .forEach(projectionService::handle);

        String marketplaceJson = mockMvc.perform(get("/api/v1/auctions/marketplace"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode marketplaceAuction = StreamSupport.stream(
                        objectMapper.readTree(marketplaceJson).path("content").spliterator(), false)
                .filter(node -> auctionId.equals(node.path("auctionId").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(marketplaceAuction.path("status").asText()).isEqualTo("FUNDING");
        assertThat(marketplaceAuction.path("investorTeaPct").decimalValue()).isEqualByComparingTo("15.000000");
    }

    @Test
    void serializesConcurrentInvestmentsSoTheAuctionCannotBeOverfunded() throws Exception {
        var auctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-concurrency"),
                new UserId("mype-concurrency"),
                new Money(new BigDecimal("10000.00"), Currency.PEN),
                false,
                "20123456789",
                "Pagador Concurrente S.A.",
                LocalDate.of(2026, 11, 6)
        ));
        auctionCommandService.handle(new EvaluateAuctionCommand(
                auctionId, "assessment-concurrency", ScoreGrade.B, true, Instant.parse("2026-09-07T17:00:00Z")
        ));
        var quote = auctionCommandService.handle(new CreateFinancialQuoteCommand(auctionId, new UserId("mype-concurrency")));
        auctionCommandService.handle(new AcceptFinancialQuoteCommand(auctionId, quote.getId(), new UserId("mype-concurrency")));

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Boolean>> investments = List.of(
                    () -> attemptInvestment(auctionId, "investor-concurrency-1", "transaction-concurrency-1"),
                    () -> attemptInvestment(auctionId, "investor-concurrency-2", "transaction-concurrency-2")
            );
            long successes = executor.invokeAll(investments).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .count();
            assertThat(successes).isEqualTo(1);
        }

        var auction = auctionQueryService.handle(new GetAuctionByIdQuery(auctionId)).orElseThrow();
        assertThat(auction.getCurrentFunding().amount()).isEqualByComparingTo("6000.00");
        assertThat(auction.getPartitions()).hasSize(1);
    }

    @Test
    void onlyTheOwningMypeCanQuoteReadTheActiveQuoteAndAcceptIt() throws Exception {
        var auctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-quote-owner"), new UserId("mype-quote-owner"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));
        auctionCommandService.handle(new EvaluateAuctionCommand(
                auctionId, "assessment-quote-owner", ScoreGrade.B, true, Instant.parse("2026-09-07T17:00:00Z")
        ));
        String quotes = "/api/v1/auctions/{auctionId}/quotes";
        String activeQuote = "/api/v1/auctions/{auctionId}/quotes/active";

        mockMvc.perform(post(quotes, auctionId.uuid()).header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(quotes, auctionId.uuid()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(activeQuote, auctionId.uuid()).header("X-User-Id", "mype-quote-owner"))
                .andExpect(status().isNotFound());

        String quoteId = objectMapper.readTree(
                mockMvc.perform(post(quotes, auctionId.uuid()).header("X-User-Id", "mype-quote-owner"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("quoteId").asText();

        mockMvc.perform(get(activeQuote, auctionId.uuid()).header("X-User-Id", "mype-quote-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quoteId").value(quoteId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get(activeQuote, auctionId.uuid()).header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(activeQuote, auctionId.uuid()))
                .andExpect(status().isForbidden());

        String accept = "/api/v1/auctions/{auctionId}/quotes/{quoteId}/accept";
        mockMvc.perform(post(accept, auctionId.uuid(), quoteId).header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(accept, auctionId.uuid(), quoteId))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(accept, auctionId.uuid(), quoteId).header("X-User-Id", "mype-quote-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get(activeQuote, auctionId.uuid()).header("X-User-Id", "mype-quote-owner"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(activeQuote, "missing-auction").header("X-User-Id", "mype-quote-owner"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsAuctionsForTheirOwningMypeAndRejectsOtherCallers() throws Exception {
        var auctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-mype-query"), new UserId("mype-query-owner"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));

        mockMvc.perform(get("/api/v1/auctions/mype/{mypeId}", "mype-query-owner")
                        .header("X-User-Id", "mype-query-owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.auctionId=='" + auctionId.uuid() + "')]").exists());

        mockMvc.perform(get("/api/v1/auctions/mype/{mypeId}", "mype-query-owner")
                        .header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auctions/mype/{mypeId}", "mype-query-owner"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAuctionsForAParticipatingInvestorAndRejectsOtherCallers() throws Exception {
        var auctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-investor-query"), new UserId("mype-investor-query"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));
        auctionCommandService.handle(new EvaluateAuctionCommand(
                auctionId, "assessment-investor-query", ScoreGrade.B, true, Instant.parse("2026-09-07T17:00:00Z")
        ));
        var quote = auctionCommandService.handle(new CreateFinancialQuoteCommand(auctionId, new UserId("mype-investor-query")));
        auctionCommandService.handle(new AcceptFinancialQuoteCommand(auctionId, quote.getId(), new UserId("mype-investor-query")));
        auctionCommandService.handle(new AddPartitionCommand(
                auctionId, new UserId("investor-query"),
                new Money(new BigDecimal("500.00"), Currency.PEN), "tx-investor-query"
        ));

        mockMvc.perform(get("/api/v1/auctions/investor/{investorId}", "investor-query")
                        .header("X-User-Id", "investor-query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.auctionId=='" + auctionId.uuid() + "')]").exists());

        mockMvc.perform(get("/api/v1/auctions/investor/{investorId}", "investor-query")
                        .header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllActiveAuctionsQueryOnlyReturnsPublishedOrFundingAuctions() {
        var pendingAuctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-active-filter-pending"), new UserId("mype-active-filter"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));

        var activeAuctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-active-filter-active"), new UserId("mype-active-filter"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));
        auctionCommandService.handle(new EvaluateAuctionCommand(
                activeAuctionId, "assessment-active-filter", ScoreGrade.B, true, Instant.parse("2026-09-07T17:00:00Z")
        ));
        var quote = auctionCommandService.handle(new CreateFinancialQuoteCommand(activeAuctionId, new UserId("mype-active-filter")));
        auctionCommandService.handle(new AcceptFinancialQuoteCommand(activeAuctionId, quote.getId(), new UserId("mype-active-filter")));

        var activeIds = auctionQueryService.handle(new GetAllActiveAuctionsQuery()).stream()
                .map(a -> a.getId().uuid())
                .toList();

        assertThat(activeIds).contains(activeAuctionId.uuid());
        assertThat(activeIds).doesNotContain(pendingAuctionId.uuid());
    }

    @Test
    void publishesPartitionAddedAndAuctionFullyFundedEventsWhenFundingCompletes(ApplicationEvents events) {
        var auctionId = auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId("invoice-events"), new UserId("mype-events"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador Eventos S.A.", LocalDate.of(2026, 11, 6)
        ));
        auctionCommandService.handle(new EvaluateAuctionCommand(
                auctionId, "assessment-events", ScoreGrade.B, true, Instant.parse("2026-09-07T17:00:00Z")
        ));
        var quote = auctionCommandService.handle(new CreateFinancialQuoteCommand(auctionId, new UserId("mype-events")));
        auctionCommandService.handle(new AcceptFinancialQuoteCommand(auctionId, quote.getId(), new UserId("mype-events")));

        auctionCommandService.handle(new AddPartitionCommand(
                auctionId, new UserId("investor-events-1"),
                new Money(new BigDecimal("500.00"), Currency.PEN), "tx-events-partial"
        ));
        auctionCommandService.handle(new AddPartitionCommand(
                auctionId, new UserId("investor-events-2"),
                new Money(new BigDecimal("9269.76"), Currency.PEN), "tx-events-final"
        ));

        List<PartitionAddedEvent> partitionEvents = events.stream(PartitionAddedEvent.class)
                .filter(event -> event.auctionId().equals(auctionId.uuid()))
                .toList();
        assertThat(partitionEvents).hasSize(2);
        assertThat(partitionEvents.get(1).newCurrentFunding()).isEqualByComparingTo("9769.76");

        List<AuctionFullyFundedEvent> fullyFundedEvents = events.stream(AuctionFullyFundedEvent.class)
                .filter(event -> event.auctionId().equals(auctionId.uuid()))
                .toList();
        assertThat(fullyFundedEvents).hasSize(1);
    }

    @Test
    void publishesTheFixedRateContractInOpenApi() throws Exception {
        String specification = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode api = objectMapper.readTree(specification);
        JsonNode investmentProperties = api.path("components")
                .path("schemas")
                .path("CreateInvestmentResource")
                .path("properties");

        assertThat(investmentProperties.has("amount")).isTrue();
        assertThat(investmentProperties.has("transactionId")).isTrue();
        assertThat(investmentProperties.has("returnRate")).isFalse();
        assertThat(api.path("paths").has("/api/v1/auctions/{auctionId}/quotes/{quoteId}/accept")).isTrue();
    }

    private boolean attemptInvestment(AuctionId auctionId, String investorId, String transactionId) {
        try {
            auctionCommandService.handle(new AddPartitionCommand(
                    auctionId,
                    new UserId(investorId),
                    new Money(new BigDecimal("6000.00"), Currency.PEN),
                    transactionId
            ));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-07T17:00:00Z"), ZoneOffset.UTC);
        }
    }
}
