package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;

import java.time.Instant;

public record EvaluateAuctionCommand(
        AuctionId auctionId,
        String assessmentId,
        ScoreGrade riskGrade,
        boolean fullBalanceOutstanding,
        Instant assessedAt
) {
}
