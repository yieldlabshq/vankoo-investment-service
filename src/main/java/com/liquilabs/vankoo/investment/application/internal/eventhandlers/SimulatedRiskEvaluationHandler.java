package com.liquilabs.vankoo.investment.application.internal.eventhandlers;

import com.liquilabs.vankoo.investment.domain.model.commands.EvaluateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * TEMPORARY STAND-IN for the risk service, which is not part of this sprint.
 *
 * <p>Without a risk service nothing ever calls the evaluation, so every auction stays in
 * {@code PENDING_VERIFICATION_RISK}: it can never be quoted, accepted or reach the
 * marketplace. When {@code vankoo.investment.risk-simulation.enabled} is true, this handler
 * evaluates each new auction right after it is created, through the same command the
 * internal {@code PUT /internal/auctions/{id}/evaluation} endpoint uses.
 *
 * <p>The grade is not a risk assessment: it is derived from the auction id so that the
 * marketplace shows a spread of A, B and C while a redelivered event still produces the same
 * grade — {@code Auction.evaluate} rejects a repeated assessment id with different data.
 *
 * <p>Delete this class and its property once the real risk service calls the evaluation.
 * See {@code docs/SIMULATED_RISK_EVALUATION.md}.
 */
@Component
@ConditionalOnProperty(prefix = "vankoo.investment.risk-simulation", name = "enabled", havingValue = "true")
public class SimulatedRiskEvaluationHandler {

    static final String ASSESSMENT_ID_PREFIX = "simulated-";

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatedRiskEvaluationHandler.class);
    private static final ScoreGrade[] GRADES = {ScoreGrade.A, ScoreGrade.B, ScoreGrade.C};

    private final AuctionCommandService auctionCommandService;
    private final TransactionTemplate newTransaction;
    private final Clock clock;

    public SimulatedRiskEvaluationHandler(
            AuctionCommandService auctionCommandService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.auctionCommandService = auctionCommandService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Runs after the creating transaction commits, so the auction is already stored, and in a
     * transaction of its own. A failure leaves the auction pending, exactly as it would be
     * without the simulation, and it can still be evaluated by hand.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AuctionCreatedEvent event) {
        ScoreGrade grade = gradeFor(event.auctionId());
        try {
            newTransaction.executeWithoutResult(status -> auctionCommandService.handle(new EvaluateAuctionCommand(
                    new AuctionId(event.auctionId()),
                    ASSESSMENT_ID_PREFIX + event.auctionId(),
                    grade,
                    true,
                    clock.instant()
            )));
            LOGGER.warn("SIMULATED risk evaluation: auction {} graded {} without a real assessment",
                    event.auctionId(), grade);
        } catch (RuntimeException e) {
            LOGGER.error("SIMULATED risk evaluation failed for auction {}; it stays pending: {}",
                    event.auctionId(), e.getMessage(), e);
        }
    }

    static ScoreGrade gradeFor(String auctionId) {
        return GRADES[Math.floorMod(auctionId.hashCode(), GRADES.length)];
    }
}
