package com.liquilabs.vankoo.investment.application.internal.eventhandlers;

import com.liquilabs.vankoo.investment.domain.model.commands.AuctionLifecycleProjectionEventType;
import com.liquilabs.vankoo.investment.domain.model.commands.ProjectAuctionLifecycleEventCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionStatus;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Currency;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.domain.services.AuctionMarketplaceProjectionService;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.AuctionLifecycleEventParser;
import com.liquilabs.vankoo.investment.infrastructure.messaging.projection.MarketplaceProjectionUpdatedEvent;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewEntity;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.AuctionMarketplaceViewRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEvent;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEventRepository;
import com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views.MarketplaceProcessedEventStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Service
public class AuctionMarketplaceProjectionServiceImpl implements AuctionMarketplaceProjectionService {

    private static final String MISSING_PUBLISHED_VIEW = "AuctionPublished must be projected first";

    private final AuctionMarketplaceViewRepository viewRepository;
    private final MarketplaceProcessedEventRepository processedEventRepository;
    private final AuctionLifecycleEventParser eventParser;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AuctionMarketplaceProjectionServiceImpl(
            AuctionMarketplaceViewRepository viewRepository,
            MarketplaceProcessedEventRepository processedEventRepository,
            AuctionLifecycleEventParser eventParser,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.viewRepository = viewRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventParser = eventParser;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(ProjectAuctionLifecycleEventCommand command) {
        if (processedEventRepository.existsById(command.eventId())) {
            return;
        }

        Instant now = clock.instant();
        MarketplaceProcessedEvent inboxEvent = processedEventRepository.save(
                new MarketplaceProcessedEvent(command, now)
        );

        if (apply(command)) {
            inboxEvent.markApplied(now);
            drainDeferredEvents(command.aggregateId(), now);
            eventPublisher.publishEvent(new MarketplaceProjectionUpdatedEvent(command.aggregateId()));
        } else {
            inboxEvent.defer(MISSING_PUBLISHED_VIEW);
        }
    }

    private boolean apply(ProjectAuctionLifecycleEventCommand command) {
        AuctionMarketplaceViewEntity view = viewRepository.findById(command.aggregateId()).orElse(null);
        if (view != null && command.sequence() <= view.getLastEventSequence()) {
            return true;
        }

        return switch (command.eventType()) {
            case AuctionCreated -> {
                applyCreated(command, view);
                yield true;
            }
            case AuctionPublished -> {
                applyPublished(command, view);
                yield true;
            }
            case PartitionAdded -> applyToPublishedView(command, view,
                    published -> published.addPartition(
                            requiredDecimal(command.data(), "currentFunding"),
                            command.sequence(), command.eventId(), command.occurredAt()
                    ));
            case AuctionFullyFunded -> applyToPublishedView(command, view,
                    published -> published.markFullyFunded(
                            command.sequence(), command.eventId(), command.occurredAt()
                    ));
            case AuctionExpired -> applyToPublishedView(command, view,
                    published -> published.expire(
                            command.sequence(), command.eventId(), command.occurredAt()
                    ));
            case AuctionCancelled -> applyToPublishedView(command, view,
                    published -> published.cancel(
                            command.sequence(), command.eventId(), command.occurredAt()
                    ));
            case AuctionClosed -> applyToPublishedView(command, view,
                    published -> published.close(
                            command.sequence(), command.eventId(), command.occurredAt()
                    ));
        };
    }

    private void applyCreated(ProjectAuctionLifecycleEventCommand command, AuctionMarketplaceViewEntity view) {
        Map<String, Object> data = command.data();
        if (view == null) {
            view = AuctionMarketplaceViewEntity.preliminary(
                    command.aggregateId(),
                    requiredString(data, "invoiceId"),
                    requiredString(data, "mypeId"),
                    requiredString(data, "payerRuc"),
                    requiredString(data, "payerName"),
                    requiredDate(data, "dueDate"),
                    requiredDecimal(data, "invoiceAmount"),
                    requiredEnum(data, "currency", Currency.class),
                    requiredStatus(data, AuctionStatus.PENDING_VERIFICATION_RISK),
                    requiredBoolean(data, "greenCertified"),
                    command.sequence(), command.eventId(), command.occurredAt()
            );
        } else {
            view.applyCreated(
                    requiredString(data, "invoiceId"),
                    requiredString(data, "mypeId"),
                    requiredString(data, "payerRuc"),
                    requiredString(data, "payerName"),
                    requiredDate(data, "dueDate"),
                    requiredDecimal(data, "invoiceAmount"),
                    requiredEnum(data, "currency", Currency.class),
                    requiredStatus(data, AuctionStatus.PENDING_VERIFICATION_RISK),
                    requiredBoolean(data, "greenCertified"),
                    command.sequence(), command.eventId(), command.occurredAt()
            );
        }
        viewRepository.save(view);
    }

    private void applyPublished(ProjectAuctionLifecycleEventCommand command, AuctionMarketplaceViewEntity view) {
        Map<String, Object> data = command.data();
        if (view == null) {
            view = AuctionMarketplaceViewEntity.preliminary(
                    command.aggregateId(),
                    requiredString(data, "invoiceId"),
                    requiredString(data, "mypeId"),
                    requiredString(data, "payerRuc"),
                    requiredString(data, "payerName"),
                    requiredDate(data, "dueDate"),
                    requiredDecimal(data, "invoiceAmount"),
                    requiredEnum(data, "currency", Currency.class),
                    AuctionStatus.PENDING_VERIFICATION_RISK,
                    requiredBoolean(data, "greenCertified"),
                    command.sequence(), command.eventId(), command.occurredAt()
            );
        }

        view.publish(
                requiredString(data, "invoiceId"),
                requiredString(data, "mypeId"),
                requiredString(data, "payerRuc"),
                requiredString(data, "payerName"),
                requiredDate(data, "dueDate"),
                requiredDecimal(data, "invoiceAmount"),
                requiredDecimal(data, "fundableAmount"),
                requiredDecimal(data, "targetAmount"),
                requiredDecimal(data, "currentFunding"),
                requiredEnum(data, "currency", Currency.class),
                requiredDecimal(data, "investorTea"),
                requiredDecimal(data, "investorTermRate"),
                requiredInteger(data, "quotedTermDays"),
                requiredEnum(data, "riskGrade", ScoreGrade.class),
                requiredStatus(data, AuctionStatus.PUBLISHED),
                requiredBoolean(data, "greenCertified"),
                requiredInstant(data, "publishedAt"),
                requiredInstant(data, "expiresAt"),
                command.sequence(), command.eventId(), command.occurredAt()
        );
        viewRepository.save(view);
    }

    private boolean applyToPublishedView(
            ProjectAuctionLifecycleEventCommand command,
            AuctionMarketplaceViewEntity view,
            ViewMutation mutation
    ) {
        if (view == null || !view.isCommerciallyComplete()) {
            return false;
        }
        mutation.apply(view);
        viewRepository.save(view);
        return true;
    }

    private void drainDeferredEvents(String aggregateId, Instant now) {
        var deferredEvents = processedEventRepository.findByAggregateIdAndStatusOrderBySequenceAsc(
                aggregateId,
                MarketplaceProcessedEventStatus.DEFERRED
        );
        for (MarketplaceProcessedEvent deferredEvent : deferredEvents) {
            ProjectAuctionLifecycleEventCommand deferredCommand = eventParser.parse(deferredEvent.getPayload());
            if (!apply(deferredCommand)) {
                break;
            }
            deferredEvent.markApplied(now);
        }
    }

    private static String requiredString(Map<String, Object> data, String name) {
        Object value = data.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidField(name);
        }
        return text;
    }

    private static BigDecimal requiredDecimal(Map<String, Object> data, String name) {
        Object value = data.get(name);
        try {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number || value instanceof String) {
                return new BigDecimal(value.toString());
            }
        } catch (NumberFormatException exception) {
            throw invalidField(name);
        }
        throw invalidField(name);
    }

    private static int requiredInteger(Map<String, Object> data, String name) {
        Object value = data.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw invalidField(name);
        }
    }

    private static boolean requiredBoolean(Map<String, Object> data, String name) {
        Object value = data.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw invalidField(name);
    }

    private static LocalDate requiredDate(Map<String, Object> data, String name) {
        try {
            return LocalDate.parse(requiredString(data, name));
        } catch (RuntimeException exception) {
            throw invalidField(name);
        }
    }

    private static Instant requiredInstant(Map<String, Object> data, String name) {
        try {
            return Instant.parse(requiredString(data, name));
        } catch (RuntimeException exception) {
            throw invalidField(name);
        }
    }

    private static <T extends Enum<T>> T requiredEnum(Map<String, Object> data, String name, Class<T> type) {
        try {
            return Enum.valueOf(type, requiredString(data, name));
        } catch (RuntimeException exception) {
            throw invalidField(name);
        }
    }

    private static AuctionStatus requiredStatus(Map<String, Object> data, AuctionStatus expected) {
        AuctionStatus status = requiredEnum(data, "status", AuctionStatus.class);
        if (status != expected) {
            throw invalidField("status");
        }
        return status;
    }

    private static IllegalArgumentException invalidField(String name) {
        return new IllegalArgumentException("Invalid or missing auction lifecycle data field: " + name);
    }

    @FunctionalInterface
    private interface ViewMutation {
        void apply(AuctionMarketplaceViewEntity view);
    }
}
