package com.liquilabs.vankoo.investment.domain.model.aggregates;

import com.liquilabs.vankoo.investment.domain.exceptions.UnauthorizedAccessException;
import com.liquilabs.vankoo.investment.domain.model.entities.AuctionFinancialQuote;
import com.liquilabs.vankoo.investment.domain.model.entities.Partition;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCancelledEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionClosedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionExpiredEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionFullyFundedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionPublishedEvent;
import com.liquilabs.vankoo.investment.domain.model.events.PartitionAddedEvent;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.domain.services.FinancialCalculation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.math.BigDecimal.ZERO;

@Entity
@Table(name = "auctions")
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Auction extends AbstractAggregateRoot<Auction> implements Persistable<AuctionId> {

    private static final MathContext MATH_CONTEXT = MathContext.DECIMAL128;

    @EmbeddedId
    @AttributeOverride(name = "uuid", column = @Column(name = "id", length = 36))
    private AuctionId id;

    @Embedded
    @AttributeOverride(name = "uuid", column = @Column(name = "invoice_id", nullable = false, unique = true, length = 36))
    private InvoiceId invoiceId;

    @Embedded
    @AttributeOverride(name = "uuid", column = @Column(name = "mype_id", nullable = false, length = 36))
    private UserId mypeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuctionStatus status;

    @Embedded
    @AttributeOverride(name = "grade", column = @Column(name = "risk_grade", nullable = false, length = 20))
    private RiskScore riskScore;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "invoice_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "invoice_currency", nullable = false, length = 3))
    })
    private Money invoiceAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "fundable_amount", precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "fundable_currency", length = 3))
    })
    private Money fundableAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "target_amount", precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "target_currency", length = 3))
    })
    private Money targetAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "current_funding_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "current_funding_currency", nullable = false, length = 3))
    })
    private Money currentFunding;

    @Column(nullable = false, length = 20)
    private String payerRuc;

    @Column(nullable = false, length = 200)
    private String payerName;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean greenCertified;

    @Column(nullable = false)
    private boolean fullBalanceOutstandingConfirmed;

    @Column(length = 100)
    private String assessmentId;

    private Instant assessedAt;

    @Column(length = 36)
    private String acceptedQuoteId;

    @Column(length = 100)
    private String closingTransactionId;

    @Column(length = 500)
    private String cancellationReason;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant publishedAt;
    private Instant expiresAt;
    private Instant closedAt;
    private Instant cancelledAt;

    @Version
    private long version;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "auction_id", nullable = false)
    @OrderBy("createdAt ASC")
    private Set<AuctionFinancialQuote> quotes = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "auction_id", nullable = false)
    @OrderBy("purchasedAt ASC")
    private List<Partition> partitions = new ArrayList<>();

    public Auction(
            InvoiceId invoiceId,
            UserId mypeId,
            Money invoiceAmount,
            boolean greenCertified,
            String payerRuc,
            String payerName,
            LocalDate dueDate
    ) {
        this.id = new AuctionId();
        this.invoiceId = Objects.requireNonNull(invoiceId);
        this.mypeId = Objects.requireNonNull(mypeId);
        this.invoiceAmount = requirePositive(invoiceAmount, "Invoice amount");
        this.riskScore = RiskScore.pendingEvaluation();
        this.greenCertified = greenCertified;
        this.payerRuc = requireText(payerRuc, "Payer RUC");
        this.payerName = requireText(payerName, "Payer name");
        this.dueDate = Objects.requireNonNull(dueDate, "Due date is required");
        this.status = AuctionStatus.PENDING_VERIFICATION_RISK;
        this.currentFunding = new Money(ZERO.setScale(2), invoiceAmount.currency());
    }

    public void registerAuctionCreatedEvent(Instant occurredAt) {
        registerEvent(new AuctionCreatedEvent(
                id.uuid(), invoiceId.uuid(), mypeId.uuid(), payerRuc, payerName,
                dueDate, invoiceAmount.amount(), invoiceAmount.currency().name(), status, greenCertified,
                Objects.requireNonNull(occurredAt)
        ));
    }

    @Override
    public AuctionId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return createdAt == null;
    }

    public void evaluate(String newAssessmentId, ScoreGrade grade, boolean fullBalanceOutstanding, Instant assessmentTime) {
        if (status != AuctionStatus.PENDING_VERIFICATION_RISK && status != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Auction can no longer be evaluated");
        }
        if (grade == null || grade == ScoreGrade.UNDER_EVALUATION) {
            throw new IllegalArgumentException("Risk grade must be A, B or C");
        }
        if (!fullBalanceOutstanding) {
            throw new IllegalArgumentException("Pilot only accepts invoices whose full balance remains outstanding");
        }

        String normalizedAssessmentId = requireText(newAssessmentId, "Assessment ID");
        if (normalizedAssessmentId.equals(assessmentId)) {
            if (riskScore.grade() != grade || !fullBalanceOutstandingConfirmed) {
                throw new IllegalStateException("Assessment ID was already used with different data");
            }
            return;
        }

        quotes.forEach(AuctionFinancialQuote::supersede);
        riskScore = new RiskScore(grade);
        fundableAmount = invoiceAmount;
        fullBalanceOutstandingConfirmed = true;
        assessmentId = normalizedAssessmentId;
        assessedAt = Objects.requireNonNull(assessmentTime);
        status = AuctionStatus.DRAFT;
    }

    public AuctionFinancialQuote createQuote(FinancialCalculation calculation, Instant now, Duration validity) {
        if (status != AuctionStatus.DRAFT || !fullBalanceOutstandingConfirmed || fundableAmount == null) {
            throw new IllegalStateException("Auction must be evaluated before it can be quoted");
        }
        if (calculation.riskGrade() != riskScore.grade()
                || calculation.currency() != fundableAmount.currency()
                || calculation.fundableAmount().compareTo(fundableAmount.amount()) != 0) {
            throw new IllegalArgumentException("Financial calculation does not match the evaluated auction");
        }

        quotes.forEach(quote -> {
            quote.expireIfNecessary(now);
            quote.supersede();
        });
        AuctionFinancialQuote quote = new AuctionFinancialQuote(calculation, now, validity);
        quotes.add(quote);
        return quote;
    }

    public void acceptQuote(String quoteId, Instant now, Duration fundingWindow, Duration settlementBuffer, ZoneId zoneId) {
        if (acceptedQuoteId != null && acceptedQuoteId.equals(quoteId)
                && (status == AuctionStatus.PUBLISHED
                || status == AuctionStatus.FUNDING
                || status == AuctionStatus.FULLY_FUNDED
                || status == AuctionStatus.CLOSED)) {
            return;
        }
        if (status != AuctionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft auction can accept a quote");
        }
        AuctionFinancialQuote quote = quoteById(quoteId)
                .orElseThrow(() -> new IllegalArgumentException("Financial quote does not belong to this auction"));

        Instant dueDateLimit = dueDate.atStartOfDay(zoneId).toInstant().minus(settlementBuffer);
        Instant windowLimit = now.plus(fundingWindow);
        Instant calculatedExpiration = windowLimit.isBefore(dueDateLimit) ? windowLimit : dueDateLimit;
        if (!calculatedExpiration.isAfter(now)) {
            throw new IllegalStateException("Invoice is too close to its due date to open a funding window");
        }

        quote.accept(now);
        acceptedQuoteId = quote.getId();
        targetAmount = new Money(quote.getFundingTarget(), quote.getCurrency());
        currentFunding = new Money(ZERO.setScale(2), quote.getCurrency());
        status = AuctionStatus.PUBLISHED;
        publishedAt = now;
        expiresAt = calculatedExpiration;
        registerEvent(new AuctionPublishedEvent(
                id.uuid(), invoiceId.uuid(), mypeId.uuid(), payerRuc, payerName, dueDate,
                invoiceAmount.amount(), fundableAmount.amount(), targetAmount.amount(),
                currentFunding.amount(), targetAmount.currency().name(), quote.getInvestorTea(),
                quote.getInvestorTermRate(), quote.getTermDays(), riskScore.grade(), status,
                greenCertified, publishedAt, expiresAt
        ));
    }

    public Partition addInvestment(
            UserId investorId,
            Money amount,
            BigDecimal minimumAmount,
            String transactionId,
            Instant now
    ) {
        String normalizedTransactionId = requireText(transactionId, "Transaction ID");
        Optional<Partition> previous = partitionByTransactionId(normalizedTransactionId);
        if (previous.isPresent()) {
            Partition partition = previous.get();
            if (!partition.getInvestorId().equals(investorId)
                    || partition.getAmount().amount().compareTo(amount.amount()) != 0
                    || partition.getAmount().currency() != amount.currency()) {
                throw new IllegalStateException("Transaction ID was already used with different investment data");
            }
            return partition;
        }

        if (status != AuctionStatus.PUBLISHED && status != AuctionStatus.FUNDING) {
            throw new IllegalStateException("Auction is not accepting investments");
        }
        if (expiresAt == null || !now.isBefore(expiresAt)) {
            throw new IllegalStateException("Auction funding window has expired");
        }
        if (amount.currency() != targetAmount.currency()) {
            throw new IllegalArgumentException("Investment currency must match the auction currency");
        }
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("Investment amount must be positive");
        }

        Money remaining = targetAmount.subtract(currentFunding);
        if (amount.isGreaterThan(remaining)) {
            throw new IllegalArgumentException("Investment amount exceeds the remaining funding target");
        }
        boolean completesAuction = amount.amount().compareTo(remaining.amount()) == 0;
        if (amount.amount().compareTo(minimumAmount) < 0 && !completesAuction) {
            throw new IllegalArgumentException("Investment amount is below the minimum for " + amount.currency());
        }

        AuctionFinancialQuote quote = acceptedQuote();
        BigDecimal participationPct = amount.amount()
                .divide(targetAmount.amount(), MATH_CONTEXT)
                .multiply(new BigDecimal("100"))
                .setScale(8, RoundingMode.HALF_UP);

        BigDecimal maturityAmount;
        if (completesAuction) {
            BigDecimal alreadyAllocated = partitions.stream()
                    .filter(partition -> partition.getStatus() == PartitionStatus.ACTIVE)
                    .map(partition -> partition.getExpectedReturn().amount())
                    .reduce(ZERO, BigDecimal::add);
            maturityAmount = quote.getFundableAmount().subtract(alreadyAllocated).setScale(2, RoundingMode.HALF_UP);
        } else {
            maturityAmount = quote.getFundableAmount()
                    .multiply(amount.amount(), MATH_CONTEXT)
                    .divide(targetAmount.amount(), MATH_CONTEXT)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        Money expectedReturn = new Money(maturityAmount, amount.currency());
        BigDecimal realizedReturnPct = maturityAmount
                .divide(amount.amount(), MATH_CONTEXT)
                .subtract(BigDecimal.ONE)
                .multiply(new BigDecimal("100"))
                .setScale(8, RoundingMode.HALF_UP);

        Partition partition = new Partition(
                id, investorId, amount, new Percentage(participationPct), new Percentage(realizedReturnPct),
                expectedReturn, normalizedTransactionId, now
        );
        partitions.add(partition);
        currentFunding = currentFunding.add(amount);
        status = AuctionStatus.FUNDING;

        registerEvent(new PartitionAddedEvent(
                id.uuid(), partition.getId().uuid(), amount.amount(), currentFunding.amount(), now
        ));

        if (isFunded()) {
            status = AuctionStatus.FULLY_FUNDED;
            registerEvent(new AuctionFullyFundedEvent(id.uuid(), now));
        }
        return partition;
    }

    public void close(String transactionId, Instant now) {
        String normalizedTransactionId = requireText(transactionId, "Closing transaction ID");
        if (status == AuctionStatus.CLOSED && normalizedTransactionId.equals(closingTransactionId)) {
            return;
        }
        if (status != AuctionStatus.FULLY_FUNDED) {
            throw new IllegalStateException("Only a fully funded auction can be closed");
        }
        closingTransactionId = normalizedTransactionId;
        status = AuctionStatus.CLOSED;
        closedAt = now;
        registerEvent(new AuctionClosedEvent(id.uuid(), closingTransactionId, now));
    }

    public void cancel(String reason, Instant now, boolean allowCommittedInvestments) {
        String normalizedReason = requireText(reason, "Cancellation reason");
        if (status == AuctionStatus.CANCELLED && normalizedReason.equals(cancellationReason)) {
            return;
        }
        if (status == AuctionStatus.CLOSED || status == AuctionStatus.CANCELLED || status == AuctionStatus.EXPIRED) {
            throw new IllegalStateException("Auction is already in a terminal state");
        }
        if (status == AuctionStatus.FULLY_FUNDED) {
            throw new IllegalStateException("A fully funded auction cannot be cancelled");
        }
        if (!allowCommittedInvestments && currentFunding.amount().signum() > 0) {
            throw new IllegalStateException("Auction with committed investments requires an internal cancellation");
        }
        List<String> releasedTransactions = cancelActivePartitions();
        cancellationReason = normalizedReason;
        status = AuctionStatus.CANCELLED;
        cancelledAt = now;
        registerEvent(new AuctionCancelledEvent(id.uuid(), cancellationReason, releasedTransactions, now));
    }

    public boolean expireIfDue(Instant now) {
        if ((status != AuctionStatus.PUBLISHED && status != AuctionStatus.FUNDING)
                || expiresAt == null || now.isBefore(expiresAt)) {
            return false;
        }
        List<String> releasedTransactions = cancelActivePartitions();
        cancellationReason = "Funding window expired before reaching the target";
        status = AuctionStatus.EXPIRED;
        cancelledAt = now;
        registerEvent(new AuctionExpiredEvent(id.uuid(), releasedTransactions, now));
        return true;
    }

    public int expireQuotes(Instant now) {
        int expired = 0;
        for (AuctionFinancialQuote quote : quotes) {
            QuoteStatus previous = quote.getStatus();
            quote.expireIfNecessary(now);
            if (previous != QuoteStatus.EXPIRED && quote.getStatus() == QuoteStatus.EXPIRED) {
                expired++;
            }
        }
        return expired;
    }

    public boolean isFunded() {
        return targetAmount != null && !currentFunding.isLessThan(targetAmount);
    }

    /**
     * Only the MYPE that owns the invoice may quote or publish its auction: both change
     * what investors will be offered, and a new quote supersedes the one the MYPE is
     * looking at.
     */
    public void ensureOwnedBy(UserId requesterId) {
        if (requesterId == null || !mypeId.equals(requesterId)) {
            throw new UnauthorizedAccessException("Caller does not own this auction");
        }
    }

    /**
     * The quote the MYPE can still accept, if any. At most one is active: creating a quote
     * supersedes the previous one, and accepting it or re-evaluating the auction ends it.
     */
    public Optional<AuctionFinancialQuote> activeQuote(Instant now) {
        return quotes.stream().filter(quote -> quote.isActiveAt(now)).findFirst();
    }

    public AuctionFinancialQuote acceptedQuote() {
        if (acceptedQuoteId == null) {
            throw new IllegalStateException("Auction has no accepted financial quote");
        }
        return quoteById(acceptedQuoteId)
                .orElseThrow(() -> new IllegalStateException("Accepted financial quote is missing"));
    }

    public Optional<Partition> partitionByTransactionId(String transactionId) {
        return partitions.stream()
                .filter(partition -> transactionId.equals(partition.getInvestmentTransactionId()))
                .findFirst();
    }

    private Optional<AuctionFinancialQuote> quoteById(String quoteId) {
        return quotes.stream().filter(quote -> quote.getId().equals(quoteId)).findFirst();
    }

    private List<String> cancelActivePartitions() {
        List<String> releasedTransactions = partitions.stream()
                .filter(partition -> partition.getStatus() == PartitionStatus.ACTIVE)
                .map(Partition::getInvestmentTransactionId)
                .toList();
        partitions.forEach(Partition::cancel);
        currentFunding = new Money(ZERO.setScale(2), invoiceAmount.currency());
        return releasedTransactions;
    }

    private static Money requirePositive(Money money, String field) {
        if (money == null || money.amount().signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return money;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
