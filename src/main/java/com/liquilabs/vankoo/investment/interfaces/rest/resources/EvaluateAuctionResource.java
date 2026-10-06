package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.ScoreGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.Instant;

public record EvaluateAuctionResource(
        @NotBlank String assessmentId,
        @NotNull ScoreGrade riskGrade,
        boolean fullBalanceOutstanding,
        @NotNull @PastOrPresent Instant assessedAt
) {
}
