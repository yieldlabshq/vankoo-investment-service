package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;

@Entity
@Table(name = "auction_marketplace_views")
public class AuctionMarketplaceViewEntity {

    private static final EnumSet<AuctionStatus> TERMINAL_STATUSES = EnumSet.of(
            AuctionStatus.FULLY_FUNDED,
            AuctionStatus.CLOSED,
            AuctionStatus.EXPIRED,
            AuctionStatus.CANCELLED
    );

    @Id
    @Column(name = "auction_id", length = 36)
    private String auctionId;

    @Column(name = "invoice_id", nullable = false, length = 36)
    private String invoiceId;

    @Column(name = "mype_id", nullable = false, length = 36)
    private String mypeId;

    @Column(name = "payer_ruc", nullable = false, length = 20)
    private String payerRuc;

    @Column(name = "payer_name", nullable = false, length = 200)
    private String payerName;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "invoice_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "fundable_amount", precision = 19, scale = 2)
    private BigDecimal fundableAmount;

    @Column(name = "target_amount", precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_funding", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentFunding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "investor_tea", precision = 19, scale = 12)
    private BigDecimal investorTea;

    @Column(name = "investor_term_rate", precision = 19, scale = 12)
    private BigDecimal investorTermRate;

    @Column(name = "quoted_term_days")
    private Integer quotedTermDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", length = 20)
    private ScoreGrade riskGrade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuctionStatus status;

    @Column(name = "green_certified", nullable = false)
    private boolean greenCertified;

    @Column(name = "marketplace_visible", nullable = false)
    private boolean marketplaceVisible;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_event_sequence", nullable = false)
    private long lastEventSequence;

    @Column(name = "last_event_id", nullable = false, length = 36)
    private String lastEventId;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    @Version
    private long version;

    protected AuctionMarketplaceViewEntity() {
    }

    public static AuctionMarketplaceViewEntity preliminary(
            String auctionId,
            String invoiceId,
            String mypeId,
            String payerRuc,
            String payerName,
            LocalDate dueDate,
            BigDecimal invoiceAmount,
            Currency currency,
            AuctionStatus status,
            boolean greenCertified,
            long sequence,
            String eventId,
            Instant occurredAt
    ) {
        AuctionMarketplaceViewEntity view = new AuctionMarketplaceViewEntity();
        view.auctionId = auctionId;
        view.currentFunding = BigDecimal.ZERO.setScale(2);
        view.applyCreated(
                invoiceId, mypeId, payerRuc, payerName, dueDate, invoiceAmount, currency, status,
                greenCertified, sequence, eventId, occurredAt
        );
        return view;
    }

    public void applyCreated(
            String invoiceId,
            String mypeId,
            String payerRuc,
            String payerName,
            LocalDate dueDate,
            BigDecimal invoiceAmount,
            Currency currency,
            AuctionStatus createdStatus,
            boolean greenCertified,
            long sequence,
            String eventId,
            Instant occurredAt
    ) {
        this.invoiceId = invoiceId;
        this.mypeId = mypeId;
        this.payerRuc = payerRuc;
        this.payerName = payerName;
        this.dueDate = dueDate;
        this.invoiceAmount = invoiceAmount;
        this.currency = currency;
        this.greenCertified = greenCertified;
        if (publishedAt == null) {
            this.status = createdStatus;
            this.marketplaceVisible = false;
        }
        advance(sequence, eventId, occurredAt);
    }

    public void publish(
            String invoiceId,
            String mypeId,
            String payerRuc,
            String payerName,
            LocalDate dueDate,
            BigDecimal invoiceAmount,
            BigDecimal fundableAmount,
            BigDecimal targetAmount,
            BigDecimal currentFunding,
            Currency currency,
            BigDecimal investorTea,
            BigDecimal investorTermRate,
            int quotedTermDays,
            ScoreGrade riskGrade,
            AuctionStatus status,
            boolean greenCertified,
            Instant publishedAt,
            Instant expiresAt,
            long sequence,
            String eventId,
            Instant occurredAt
    ) {
        this.invoiceId = invoiceId;
        this.mypeId = mypeId;
        this.payerRuc = payerRuc;
        this.payerName = payerName;
        this.dueDate = dueDate;
        this.invoiceAmount = invoiceAmount;
        this.fundableAmount = fundableAmount;
        this.targetAmount = targetAmount;
        this.currentFunding = currentFunding;
        this.currency = currency;
        this.investorTea = investorTea;
        this.investorTermRate = investorTermRate;
        this.quotedTermDays = quotedTermDays;
        this.riskGrade = riskGrade;
        this.status = status;
        this.greenCertified = greenCertified;
        this.marketplaceVisible = status == AuctionStatus.PUBLISHED || status == AuctionStatus.FUNDING;
        this.publishedAt = publishedAt;
        this.expiresAt = expiresAt;
        advance(sequence, eventId, occurredAt);
    }

    public void addPartition(BigDecimal newCurrentFunding, long sequence, String eventId, Instant occurredAt) {
        if (!TERMINAL_STATUSES.contains(status)) {
            currentFunding = newCurrentFunding;
            status = AuctionStatus.FUNDING;
            marketplaceVisible = true;
        }
        advance(sequence, eventId, occurredAt);
    }

    public void markFullyFunded(long sequence, String eventId, Instant occurredAt) {
        if (!TERMINAL_STATUSES.contains(status)) {
            currentFunding = targetAmount;
            status = AuctionStatus.FULLY_FUNDED;
            marketplaceVisible = false;
        }
        advance(sequence, eventId, occurredAt);
    }

    public void expire(long sequence, String eventId, Instant occurredAt) {
        currentFunding = BigDecimal.ZERO.setScale(2);
        status = AuctionStatus.EXPIRED;
        marketplaceVisible = false;
        advance(sequence, eventId, occurredAt);
    }

    public void cancel(long sequence, String eventId, Instant occurredAt) {
        currentFunding = BigDecimal.ZERO.setScale(2);
        status = AuctionStatus.CANCELLED;
        marketplaceVisible = false;
        advance(sequence, eventId, occurredAt);
    }

    public void close(long sequence, String eventId, Instant occurredAt) {
        status = AuctionStatus.CLOSED;
        marketplaceVisible = false;
        advance(sequence, eventId, occurredAt);
    }

    private void advance(long sequence, String eventId, Instant occurredAt) {
        lastEventSequence = sequence;
        lastEventId = eventId;
        projectedAt = occurredAt;
    }

    public boolean isCommerciallyComplete() { return publishedAt != null; }
    public String getAuctionId() { return auctionId; }
    public String getInvoiceId() { return invoiceId; }
    public String getMypeId() { return mypeId; }
    public String getPayerRuc() { return payerRuc; }
    public String getPayerName() { return payerName; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getInvoiceAmount() { return invoiceAmount; }
    public BigDecimal getFundableAmount() { return fundableAmount; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getCurrentFunding() { return currentFunding; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getInvestorTea() { return investorTea; }
    public BigDecimal getInvestorTermRate() { return investorTermRate; }
    public Integer getQuotedTermDays() { return quotedTermDays; }
    public ScoreGrade getRiskGrade() { return riskGrade; }
    public AuctionStatus getStatus() { return status; }
    public boolean isGreenCertified() { return greenCertified; }
    public boolean isMarketplaceVisible() { return marketplaceVisible; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getLastEventSequence() { return lastEventSequence; }
    public String getLastEventId() { return lastEventId; }
    public Instant getProjectedAt() { return projectedAt; }
}
