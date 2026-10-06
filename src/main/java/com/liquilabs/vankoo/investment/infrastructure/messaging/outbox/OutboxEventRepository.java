package com.liquilabs.vankoo.investment.infrastructure.messaging.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByAggregateIdOrderBySequence(String aggregateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event "
            + "where event.status = :status and event.nextAttemptAt <= :now order by event.sequence")
    List<OutboxEvent> findPublishable(
            @Param("status") OutboxStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, Instant cutoff);
}
