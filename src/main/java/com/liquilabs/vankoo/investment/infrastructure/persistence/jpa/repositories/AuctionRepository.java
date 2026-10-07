package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.repositories;

import com.liquilabs.vankoo.investment.domain.model.aggregates.Auction;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, AuctionId> {

    Optional<Auction> findByInvoiceId(InvoiceId invoiceId);

    @EntityGraph(attributePaths = {"quotes", "partitions"})
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findDetailedById(@Param("id") AuctionId id);

    List<Auction> findByStatusIn(Collection<AuctionStatus> statuses);

    List<Auction> findByStatusInOrderByExpiresAtAsc(Collection<AuctionStatus> statuses);

    @Query("select a.id from Auction a where a.status in :statuses and a.expiresAt <= :now")
    List<AuctionId> findIdsDueForExpiration(
            @Param("statuses") Collection<AuctionStatus> statuses,
            @Param("now") Instant now
    );

    @Query("select distinct a.id from Auction a join a.quotes q "
            + "where a.status = :auctionStatus and q.status = :quoteStatus and q.validUntil <= :now")
    List<AuctionId> findIdsWithExpiredQuotes(
            @Param("auctionStatus") AuctionStatus auctionStatus,
            @Param("quoteStatus") QuoteStatus quoteStatus,
            @Param("now") Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") AuctionId id);

    @EntityGraph(attributePaths = {"quotes", "partitions"})
    List<Auction> findByMypeId(UserId mypeId);

    @EntityGraph(attributePaths = {"quotes", "partitions"})
    @Query("select distinct a from Auction a join a.partitions p where p.investorId = :investorId")
    List<Auction> findByInvestorParticipation(@Param("investorId") UserId investorId);
}
