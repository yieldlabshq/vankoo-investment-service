package com.liquilabs.vankoo.investment.application.internal.eventhandlers;

import com.liquilabs.vankoo.investment.domain.model.commands.CreateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.events.AuctionCreatedEvent;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAuctionByIdQuery;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.domain.services.AuctionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.stream.default-binder=test",
        "vankoo.investment.risk-simulation.enabled=true"
})
@Import(TestChannelBinderConfiguration.class)
@ActiveProfiles("test")
class SimulatedRiskEvaluationHandlerTest {

    @Autowired
    private AuctionCommandService auctionCommandService;

    @Autowired
    private AuctionQueryService auctionQueryService;

    @Autowired
    private SimulatedRiskEvaluationHandler handler;

    @Test
    void evaluatesANewAuctionSoItCanBeQuoted() {
        var auctionId = createAuction("invoice-simulated-risk");

        var auction = auctionQueryService.handle(new GetAuctionByIdQuery(auctionId)).orElseThrow();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.DRAFT);
        assertThat(auction.getRiskScore().grade())
                .isEqualTo(SimulatedRiskEvaluationHandler.gradeFor(auctionId.uuid()));
        assertThat(auction.getAssessmentId()).isEqualTo("simulated-" + auctionId.uuid());
        assertThat(auction.isFullBalanceOutstandingConfirmed()).isTrue();
        assertThat(auction.getFundableAmount().amount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void leavesTheAuctionUnchangedWhenTheSameCreationIsDeliveredAgain() {
        var auctionId = createAuction("invoice-simulated-risk-redelivery");
        var first = auctionQueryService.handle(new GetAuctionByIdQuery(auctionId)).orElseThrow();

        handler.on(new AuctionCreatedEvent(
                auctionId.uuid(), "invoice-simulated-risk-redelivery", "mype-simulated-risk",
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6),
                new BigDecimal("10000.00"), "PEN", AuctionStatus.PENDING_VERIFICATION_RISK, false,
                Instant.parse("2026-09-07T17:00:00Z")
        ));

        var again = auctionQueryService.handle(new GetAuctionByIdQuery(auctionId)).orElseThrow();
        assertThat(again.getStatus()).isEqualTo(AuctionStatus.DRAFT);
        assertThat(again.getRiskScore().grade()).isEqualTo(first.getRiskScore().grade());
        assertThat(again.getAssessedAt()).isEqualTo(first.getAssessedAt());
    }

    @Test
    void derivesAStableGradeThatStillVariesAcrossAuctions() {
        var ids = IntStream.range(0, 60).mapToObj(i -> UUID.nameUUIDFromBytes(("auction-" + i).getBytes()).toString()).toList();

        assertThat(ids).allSatisfy(id ->
                assertThat(SimulatedRiskEvaluationHandler.gradeFor(id)).isEqualTo(SimulatedRiskEvaluationHandler.gradeFor(id)));
        assertThat(ids.stream().map(SimulatedRiskEvaluationHandler::gradeFor).distinct())
                .containsExactlyInAnyOrder(ScoreGrade.A, ScoreGrade.B, ScoreGrade.C);
    }

    private AuctionId createAuction(String invoiceId) {
        return auctionCommandService.handle(new CreateAuctionCommand(
                new InvoiceId(invoiceId), new UserId("mype-simulated-risk"),
                new Money(new BigDecimal("10000.00"), Currency.PEN), false,
                "20123456789", "Pagador S.A.", LocalDate.of(2026, 11, 6)
        ));
    }
}
