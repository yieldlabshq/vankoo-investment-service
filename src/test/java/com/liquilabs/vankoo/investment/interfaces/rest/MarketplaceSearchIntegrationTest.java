package com.liquilabs.vankoo.investment.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Policy;
import com.liquilabs.vankoo.investment.domain.model.queries.GetMarketplaceAuctionsQuery;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.domain.services.AuctionMarketplaceProjectionService;
import com.liquilabs.vankoo.investment.domain.services.AuctionQueryService;
import com.liquilabs.vankoo.investment.infrastructure.configuration.MarketplaceCacheConfiguration;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.AuctionLifecycleEventParser;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewEntity;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEventRepository;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleEventContract;
import com.liquilabs.vankoo.investment.interfaces.events.resources.AuctionLifecycleIntegrationEvent;
import com.liquilabs.vankoo.investment.interfaces.events.resources.PartitionAddedIntegrationEventData;
import com.liquilabs.vankoo.investment.interfaces.rest.transform.MarketplaceQueryAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.cloud.stream.default-binder=test")
@AutoConfigureMockMvc
@Import(TestChannelBinderConfiguration.class)
@ActiveProfiles("test")
class MarketplaceSearchIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-14T20:00:00Z");
    private static final String AUCTION_A = "00000000-0000-0000-0000-000000000001";
    private static final String AUCTION_B = "00000000-0000-0000-0000-000000000002";
    private static final String AUCTION_C = "00000000-0000-0000-0000-000000000003";
    private static final String AUCTION_D = "00000000-0000-0000-0000-000000000004";
    private static final String AUCTION_PRELIMINARY = "00000000-0000-0000-0000-000000000005";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionMarketplaceViewRepository viewRepository;

    @Autowired
    private MarketplaceProcessedEventRepository processedEventRepository;

    @Autowired
    private AuctionQueryService queryService;

    @Autowired
    private AuctionMarketplaceProjectionService projectionService;

    @Autowired
    private AuctionLifecycleEventParser eventParser;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void seedMarketplace() {
        processedEventRepository.deleteAll();
        viewRepository.deleteAll();
        cacheManager.getCache(MarketplaceCacheConfiguration.MARKETPLACE_CACHE).clear();

        viewRepository.save(publishedView(
                AUCTION_A, AuctionStatus.PUBLISHED, Currency.PEN, true,
                new BigDecimal("9000.00"), NOW.plusSeconds(86_400)
        ));
        viewRepository.save(publishedView(
                AUCTION_B, AuctionStatus.FUNDING, Currency.PEN, false,
                new BigDecimal("8000.00"), NOW.plusSeconds(86_400)
        ));
        viewRepository.save(publishedView(
                AUCTION_C, AuctionStatus.FUNDING, Currency.USD, true,
                new BigDecimal("7000.00"), NOW.plusSeconds(172_800)
        ));
        viewRepository.save(publishedView(
                AUCTION_D, AuctionStatus.EXPIRED, Currency.PEN, true,
                new BigDecimal("11000.00"), NOW.plusSeconds(259_200)
        ));
        viewRepository.save(AuctionMarketplaceViewEntity.preliminary(
                AUCTION_PRELIMINARY, "invoice-preliminary", "mype-1", "20123456789", "Preliminary payer",
                LocalDate.of(2026, 12, 31), new BigDecimal("5000.00"), Currency.PEN,
                AuctionStatus.PENDING_VERIFICATION_RISK, false, 1, UUID.randomUUID().toString(), NOW
        ));
    }

    @Test
    void returnsStableDefaultPageWithOnlyActiveAuctions() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/marketplace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.sort").value("expiresAt,asc"))
                .andExpect(jsonPath("$.content[0].auctionId").value(AUCTION_A))
                .andExpect(jsonPath("$.content[1].auctionId").value(AUCTION_B))
                .andExpect(jsonPath("$.content[2].auctionId").value(AUCTION_C));
    }

    @Test
    void combinesStatusCurrencyAndGreenFiltersInTheProjectionQuery() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/marketplace")
                        .param("status", "FUNDING")
                        .param("currency", "PEN")
                        .param("greenCertified", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].auctionId").value(AUCTION_B));

        mockMvc.perform(get("/api/v1/auctions/marketplace")
                        .param("status", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].auctionId").value(AUCTION_D));
    }

    @Test
    void paginatesAndSortsWithAuctionIdAsStableTieBreaker() throws Exception {
        mockMvc.perform(get("/api/v1/auctions/marketplace")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "targetAmount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.sort").value("targetAmount,desc"))
                .andExpect(jsonPath("$.content[0].auctionId").value(AUCTION_B));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auctions/marketplace?page=-1",
            "/api/v1/auctions/marketplace?size=0",
            "/api/v1/auctions/marketplace?size=101",
            "/api/v1/auctions/marketplace?sort=unknown,asc",
            "/api/v1/auctions/marketplace?sort=expiresAt,sideways",
            "/api/v1/auctions/marketplace?status=DRAFT",
            "/api/v1/auctions/marketplace?currency=EUR"
    })
    void rejectsInvalidParameters(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isBadRequest());
    }

    @Test
    void cachesEachCompleteQueryKeyForThirtySeconds() {
        GetMarketplaceAuctionsQuery penQuery = MarketplaceQueryAssembler.toQuery(
                null, "PEN", null, 0, 20, "expiresAt,asc"
        );
        GetMarketplaceAuctionsQuery usdQuery = MarketplaceQueryAssembler.toQuery(
                null, "USD", null, 0, 20, "expiresAt,asc"
        );

        assertThat(queryService.handle(penQuery).totalElements()).isEqualTo(2);
        assertThat(queryService.handle(usdQuery).totalElements()).isOne();

        viewRepository.save(publishedView(
                "00000000-0000-0000-0000-000000000006", AuctionStatus.PUBLISHED, Currency.PEN, false,
                new BigDecimal("6000.00"), NOW.plusSeconds(345_600)
        ));

        assertThat(queryService.handle(penQuery).totalElements()).isEqualTo(2);
        assertThat(queryService.handle(usdQuery).totalElements()).isOne();

        CaffeineCache cache = (CaffeineCache) cacheManager.getCache(
                MarketplaceCacheConfiguration.MARKETPLACE_CACHE
        );
        assertThat(cache.getNativeCache().estimatedSize()).isEqualTo(2);
        Policy.FixedExpiration<Object, Object> expiration = cache.getNativeCache()
                .policy()
                .expireAfterWrite()
                .orElseThrow();
        assertThat(expiration.getExpiresAfter(TimeUnit.SECONDS)).isEqualTo(30);
    }

    @Test
    void invalidatesCachedPagesAfterProjectionCommit() throws Exception {
        GetMarketplaceAuctionsQuery query = MarketplaceQueryAssembler.toQuery(
                null, "PEN", null, 0, 20, "expiresAt,asc"
        );
        assertThat(queryService.handle(query).content())
                .filteredOn(view -> view.auctionId().equals(AUCTION_A))
                .singleElement()
                .extracting(view -> view.currentFunding())
                .isEqualTo(new BigDecimal("0.00"));

        var event = new AuctionLifecycleIntegrationEvent<>(
                UUID.randomUUID().toString(),
                AuctionLifecycleEventContract.PARTITION_ADDED,
                1,
                AUCTION_A,
                2,
                NOW.plusSeconds(2),
                new PartitionAddedIntegrationEventData(
                        "partition-cache", new BigDecimal("500.00"), new BigDecimal("500.00")
                )
        );
        projectionService.handle(eventParser.parse(objectMapper.writeValueAsBytes(event)));

        assertThat(queryService.handle(query).content())
                .filteredOn(view -> view.auctionId().equals(AUCTION_A))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.currentFunding()).isEqualByComparingTo("500.00");
                    assertThat(view.status()).isEqualTo(AuctionStatus.FUNDING);
                });
    }

    private static AuctionMarketplaceViewEntity publishedView(
            String auctionId,
            AuctionStatus desiredStatus,
            Currency currency,
            boolean greenCertified,
            BigDecimal targetAmount,
            Instant expiresAt
    ) {
        String eventId = UUID.randomUUID().toString();
        AuctionMarketplaceViewEntity view = AuctionMarketplaceViewEntity.preliminary(
                auctionId,
                "invoice-" + auctionId.substring(auctionId.length() - 4),
                "mype-1",
                "20123456789",
                "Marketplace payer",
                LocalDate.of(2026, 12, 31),
                targetAmount.add(new BigDecimal("500.00")),
                currency,
                AuctionStatus.PENDING_VERIFICATION_RISK,
                greenCertified,
                1,
                eventId,
                NOW
        );
        view.publish(
                view.getInvoiceId(), view.getMypeId(), view.getPayerRuc(), view.getPayerName(), view.getDueDate(),
                view.getInvoiceAmount(), targetAmount.add(new BigDecimal("250.00")), targetAmount,
                BigDecimal.ZERO.setScale(2), currency, new BigDecimal("0.15"), new BigDecimal("0.04"),
                90, ScoreGrade.B, AuctionStatus.PUBLISHED, greenCertified, NOW, expiresAt,
                1, eventId, NOW
        );
        if (desiredStatus == AuctionStatus.FUNDING) {
            view.addPartition(new BigDecimal("500.00"), 1, eventId, NOW);
        } else if (desiredStatus == AuctionStatus.FULLY_FUNDED) {
            view.markFullyFunded(1, eventId, NOW);
        } else if (desiredStatus == AuctionStatus.EXPIRED) {
            view.expire(1, eventId, NOW);
        } else if (desiredStatus == AuctionStatus.CANCELLED) {
            view.cancel(1, eventId, NOW);
        } else if (desiredStatus == AuctionStatus.CLOSED) {
            view.close(1, eventId, NOW);
        }
        return view;
    }
}
