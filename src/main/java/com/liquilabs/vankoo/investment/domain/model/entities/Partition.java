package com.liquilabs.vankoo.investment.domain.model.entities;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "partitions",
        uniqueConstraints = @UniqueConstraint(name = "uk_partitions_transaction", columnNames = "investment_transaction_id")
)
@Getter
@NoArgsConstructor
public class Partition {

    @EmbeddedId
    @AttributeOverride(name = "uuid", column = @Column(name = "id", length = 36))
    private PartitionId id;

    @Embedded
    @AttributeOverride(
            name = "uuid",
            column = @Column(name = "auction_id", nullable = false, length = 36, insertable = false, updatable = false)
    )
    private AuctionId auctionId;

    @Embedded
    @AttributeOverride(name = "uuid", column = @Column(name = "investor_id", nullable = false, length = 36))
    private UserId investorId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "investment_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "investment_currency", nullable = false, length = 3))
    })
    private Money amount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "participation_percentage", nullable = false, precision = 19, scale = 8))
    private Percentage percentage;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "return_rate", nullable = false, precision = 19, scale = 8))
    private Percentage returnRate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "expected_return_amount", nullable = false, precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "expected_return_currency", nullable = false, length = 3))
    })
    private Money expectedReturn;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "actual_return_amount", precision = 19, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "actual_return_currency", length = 3))
    })
    private Money actualReturn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartitionStatus status;

    @Column(nullable = false)
    private Instant purchasedAt;

    private Instant paidAt;

    @Column(nullable = false, length = 100)
    private String investmentTransactionId;

    @Column(length = 100)
    private String paymentTransactionId;

    @Version
    private long version;

    public Partition(
            AuctionId auctionId,
            UserId investorId,
            Money amount,
            Percentage percentage,
            Percentage returnRate,
            Money expectedReturn,
            String transactionId,
            Instant purchasedAt
    ) {
        this.id = new PartitionId();
        this.auctionId = auctionId;
        this.investorId = investorId;
        this.amount = amount;
        this.percentage = percentage;
        this.returnRate = returnRate;
        this.expectedReturn = expectedReturn;
        this.purchasedAt = purchasedAt;
        this.status = PartitionStatus.ACTIVE;
        this.investmentTransactionId = transactionId;
    }

    public void markAsPaid(String transactionId, Money paidAmount, Instant instant) {
        if (status != PartitionStatus.ACTIVE) {
            throw new IllegalStateException("Partition must be active to be paid");
        }
        status = PartitionStatus.PAID;
        paidAt = instant;
        paymentTransactionId = transactionId;
        actualReturn = paidAmount;
    }

    public void markAsDefaulted() {
        if (status != PartitionStatus.ACTIVE) {
            throw new IllegalStateException("Only an active partition can default");
        }
        status = PartitionStatus.DEFAULTED;
    }

    public void cancel() {
        if (status == PartitionStatus.ACTIVE) {
            status = PartitionStatus.CANCELLED;
        }
    }
}
