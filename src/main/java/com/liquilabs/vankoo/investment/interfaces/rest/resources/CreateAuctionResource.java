package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAuctionResource(
        @NotBlank String invoiceId,
        @NotBlank String mypeId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal invoiceAmount,
        @NotBlank String currency,
        boolean greenCertified,
        @NotBlank String payerRuc,
        @NotBlank String payerName,
        @NotNull @Future LocalDate dueDate
) {}
