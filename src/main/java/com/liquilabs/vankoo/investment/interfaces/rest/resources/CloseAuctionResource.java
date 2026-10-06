package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

public record CloseAuctionResource(@NotBlank String transactionId) {
}
