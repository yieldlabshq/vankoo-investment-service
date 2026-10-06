package com.liquilabs.vankoo.investment.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelAuctionResource(@NotBlank @Size(max = 500) String reason) {
}
