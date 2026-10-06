package com.liquilabs.vankoo.investment.domain.model.valueobjects;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record RiskScore(
        @Enumerated(EnumType.STRING) ScoreGrade grade
) {
    public RiskScore {
        if (grade == null) throw new IllegalArgumentException("Grade cannot be null");
    }

    public static RiskScore pendingEvaluation() {
        return new RiskScore(ScoreGrade.UNDER_EVALUATION);
    }


    public boolean isLowRisk() { return grade == ScoreGrade.A; }
    public boolean isMediumRisk() { return grade == ScoreGrade.B; }
    public boolean isHighRisk() { return grade == ScoreGrade.C; }
}