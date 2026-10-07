package com.liquilabs.vankoo.investment.application.internal.queryservices;

import com.liquilabs.vankoo.investment.domain.exceptions.AuctionNotFoundException;
import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.queries.*;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.services.AuctionQueryService;
import com.liquilabs.vankoo.investment.infrastructure.configuration.PricingProperties;
import com.liquilabs.vankoo.investment.infrastructure.configuration.MarketplaceCacheConfiguration;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.repositories.AuctionRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewEntity;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceSpecifications;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuctionQueryServiceImpl implements AuctionQueryService {

    private final AuctionRepository auctionRepository;
    private final AuctionMarketplaceViewRepository marketplaceViewRepository;
    private final PricingProperties pricingProperties;
    private final Clock clock;

    public AuctionQueryServiceImpl(
            AuctionRepository auctionRepository,
            AuctionMarketplaceViewRepository marketplaceViewRepository,
            PricingProperties pricingProperties,
            Clock clock
    ) {
        this.auctionRepository = auctionRepository;
        this.marketplaceViewRepository = marketplaceViewRepository;
        this.pricingProperties = pricingProperties;
        this.clock = clock;
    }

    @Override
    public Optional<Auction> handle(GetAuctionByIdQuery query) {
        return auctionRepository.findDetailedById(query.auctionId());
    }

    @Override
    public Optional<AuctionFinancialQuote> handle(GetActiveFinancialQuoteQuery query) {
        Auction auction = auctionRepository.findDetailedById(query.auctionId())
                .orElseThrow(() -> new AuctionNotFoundException(query.auctionId().uuid()));
        auction.ensureOwnedBy(query.requesterId());
        return auction.activeQuote(clock.instant());
    }

    @Override
    public List<Auction> handle(GetAllActiveAuctionsQuery query) {
        return auctionRepository.findByStatusIn(List.of(AuctionStatus.PUBLISHED, AuctionStatus.FUNDING));
    }

    @Override
    @Cacheable(cacheNames = MarketplaceCacheConfiguration.MARKETPLACE_CACHE, key = "#query")
    public AuctionMarketplacePage handle(GetMarketplaceAuctionsQuery query) {
        LocalDate today = LocalDate.now(clock.withZone(pricingProperties.pricingZone()));
        Sort.Direction direction = Sort.Direction.valueOf(query.sortDirection().name());
        Sort sort = Sort.by(direction, query.sortField().property())
                .and(Sort.by(Sort.Direction.ASC, "auctionId"));
        PageRequest pageRequest = PageRequest.of(query.page(), query.size(), sort);
        var page = marketplaceViewRepository.findAll(AuctionMarketplaceSpecifications.from(query), pageRequest);
        var content = page.getContent().stream()
                .map(view -> toMarketplaceView(view, today))
                .toList();
        return new AuctionMarketplacePage(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                query.sortField().property() + "," + query.sortDirection().name().toLowerCase()
        );
    }
    @Override
    public List<Auction> handle(GetAuctionsByMypeQuery query) {
        return auctionRepository.findByMypeId(query.mypeId());
    }

    @Override
    public List<Auction> handle(GetAuctionsByInvestorQuery query) {
        return auctionRepository.findByInvestorParticipation(query.investorId());
    }

    private AuctionMarketplaceView toMarketplaceView(AuctionMarketplaceViewEntity auction, LocalDate today) {
        BigDecimal available = auction.getTargetAmount().subtract(auction.getCurrentFunding());
        BigDecimal progress = auction.getCurrentFunding()
                .multiply(new BigDecimal("100"))
                .divide(auction.getTargetAmount(), 4, RoundingMode.HALF_UP);

        return new AuctionMarketplaceView(
                auction.getAuctionId(),
                auction.getInvoiceId(),
                auction.getMypeId(),
                auction.getPayerRuc(),
                auction.getPayerName(),
                auction.getTargetAmount(),
                auction.getCurrentFunding(),
                available,
                progress,
                auction.getCurrency().name(),
                toPercentagePoints(auction.getInvestorTea()),
                toPercentagePoints(auction.getInvestorTermRate()),
                auction.getQuotedTermDays(),
                Math.max(0, ChronoUnit.DAYS.between(today, auction.getDueDate())),
                auction.getRiskGrade(),
                auction.getStatus(),
                auction.getDueDate(),
                auction.getPublishedAt(),
                auction.getExpiresAt(),
                auction.isGreenCertified()
        );
    }

    private static BigDecimal toPercentagePoints(BigDecimal fraction) {
        return fraction.multiply(new BigDecimal("100")).setScale(6, RoundingMode.HALF_UP);
    }
}
