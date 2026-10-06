package com.liquilabs.vankoo.investment.application.internal.commandservices;

import com.liquilabs.vankoo.investment.domain.exceptions.AuctionNotFoundException;
import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.commands.*;
import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.entities.Partition;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.PricingParameters;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.QuoteStatus;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.domain.services.AuctionPricingCalculator;
import com.liquilabs.vankoo.investment.infrastructure.configuration.PricingProperties;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.repositories.AuctionRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class AuctionCommandServiceImpl implements AuctionCommandService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionCommandServiceImpl.class);

    private final AuctionRepository auctionRepository;
    private final AuctionPricingCalculator pricingCalculator;
    private final PricingProperties pricingProperties;
    private final Clock clock;

    public AuctionCommandServiceImpl(
            AuctionRepository auctionRepository,
            AuctionPricingCalculator pricingCalculator,
            PricingProperties pricingProperties,
            Clock clock
    ) {
        this.auctionRepository = auctionRepository;
        this.pricingCalculator = pricingCalculator;
        this.pricingProperties = pricingProperties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AuctionId handle(CreateAuctionCommand command) {
        var existing = auctionRepository.findByInvoiceId(command.invoiceId());
        if (existing.isPresent()) {
            LOGGER.info("Auction already exists for invoice {}. Reusing {}",
                    command.invoiceId().uuid(), existing.get().getId().uuid());
            return existing.get().getId();
        }

        var auction = new Auction(
                command.invoiceId(),
                command.mypeId(),
                command.invoiceAmount(),
                command.greenCertified(),
                command.payerRuc(),
                command.payerName(),
                command.dueDate()
        );
        auction.registerAuctionCreatedEvent(clock.instant());
        auctionRepository.save(auction);
        return auction.getId();
    }

    @Override
    @Transactional
    public Auction handle(EvaluateAuctionCommand command) {
        Auction auction = locked(command.auctionId());
        auction.evaluate(
                command.assessmentId(),
                command.riskGrade(),
                command.fullBalanceOutstanding(),
                command.assessedAt()
        );
        return auctionRepository.save(auction);
    }

    @Override
    @Transactional
    public AuctionFinancialQuote handle(CreateFinancialQuoteCommand command) {
        Auction auction = locked(command.auctionId());
        auction.ensureOwnedBy(command.requesterId());
        if (auction.getStatus() != AuctionStatus.DRAFT
                || !auction.isFullBalanceOutstandingConfirmed()
                || auction.getFundableAmount() == null) {
            throw new IllegalStateException("Auction must be evaluated before it can be quoted");
        }
        var now = clock.instant();
        LocalDate valuationDate = LocalDate.now(clock.withZone(pricingProperties.pricingZone()));
        var pricingParameters = new PricingParameters(
                pricingProperties.version(),
                pricingProperties.dayCountBasis(),
                pricingProperties.platformMonthlyFeeRate(),
                pricingProperties.platformFeeTaxRate(),
                pricingProperties.investorTea()
        );
        var calculation = pricingCalculator.calculate(
                pricingParameters,
                auction.getFundableAmount(),
                auction.getRiskScore().grade(),
                valuationDate,
                auction.getDueDate()
        );
        AuctionFinancialQuote quote = auction.createQuote(calculation, now, pricingProperties.quoteValidity());
        auctionRepository.save(auction);
        return quote;
    }

    @Override
    @Transactional
    public Auction handle(AcceptFinancialQuoteCommand command) {
        Auction auction = locked(command.auctionId());
        auction.ensureOwnedBy(command.requesterId());
        auction.acceptQuote(
                command.quoteId(),
                clock.instant(),
                pricingProperties.fundingWindow(),
                pricingProperties.settlementBuffer(),
                pricingProperties.pricingZone()
        );
        auction.acceptedQuote();
        return auctionRepository.save(auction);
    }

    @Override
    @Transactional
    public Partition handle(AddPartitionCommand command) {
        Auction auction = locked(command.auctionId());
        Partition partition = auction.addInvestment(
                command.investorId(),
                command.amount(),
                pricingProperties.minimumFor(command.amount().currency()),
                command.transactionId(),
                clock.instant()
        );
        auctionRepository.save(auction);
        return partition;
    }

    @Override
    @Transactional
    public Auction handle(CloseAuctionCommand command) {
        Auction auction = locked(command.auctionId());
        auction.close(command.transactionId(), clock.instant());
        auction.acceptedQuote();
        return auctionRepository.save(auction);
    }

    @Override
    @Transactional
    public Auction handle(CancelAuctionCommand command) {
        Auction auction = locked(command.auctionId());
        auction.cancel(command.reason(), clock.instant(), command.internal());
        if (auction.getAcceptedQuoteId() != null) {
            auction.acceptedQuote();
        }
        return auctionRepository.save(auction);
    }

    @Override
    @Transactional
    public int expireDueAuctions() {
        var now = clock.instant();
        List<AuctionId> ids = auctionRepository.findIdsDueForExpiration(
                List.of(AuctionStatus.PUBLISHED, AuctionStatus.FUNDING), now
        );
        int expired = 0;
        for (AuctionId id : ids) {
            Auction auction = locked(id);
            if (auction.expireIfDue(now)) {
                auctionRepository.save(auction);
                expired++;
            }
        }
        return expired;
    }

    @Override
    @Transactional
    public int expireDueQuotes() {
        var now = clock.instant();
        List<AuctionId> ids = auctionRepository.findIdsWithExpiredQuotes(
                AuctionStatus.DRAFT, QuoteStatus.ACTIVE, now
        );
        int expired = 0;
        for (AuctionId id : ids) {
            Auction auction = locked(id);
            expired += auction.expireQuotes(now);
            auctionRepository.save(auction);
        }
        return expired;
    }

    private Auction locked(AuctionId auctionId) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new AuctionNotFoundException(auctionId.uuid()));
    }
}
