package com.liquilabs.vankoo.investment.interfaces.rest.controllers;

import com.liquilabs.vankoo.investment.domain.model.commands.CancelAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.CloseAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.commands.EvaluateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.AuctionDetailsResource;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.CancelAuctionResource;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.CloseAuctionResource;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.EvaluateAuctionResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/auctions")
@Tag(name = "Internal Auctions", description = "Network-private bridge endpoints for underwriting and operations")
public class InternalAuctionsController {

    private final AuctionCommandService auctionCommandService;

    public InternalAuctionsController(AuctionCommandService auctionCommandService) {
        this.auctionCommandService = auctionCommandService;
    }

    @PutMapping("/{auctionId}/evaluation")
    @Operation(summary = "Assign risk and confirm the pilot outstanding-balance invariant")
    public AuctionDetailsResource evaluate(
            @PathVariable String auctionId,
            @Valid @RequestBody EvaluateAuctionResource resource
    ) {
        var auction = auctionCommandService.handle(new EvaluateAuctionCommand(
                new AuctionId(auctionId),
                resource.assessmentId(),
                resource.riskGrade(),
                resource.fullBalanceOutstanding(),
                resource.assessedAt()
        ));
        return AuctionDetailsResource.from(auction);
    }

    @PostMapping("/{auctionId}/close")
    @Operation(summary = "Confirm operational closure of a fully funded auction")
    public AuctionDetailsResource close(
            @PathVariable String auctionId,
            @Valid @RequestBody CloseAuctionResource resource
    ) {
        var auction = auctionCommandService.handle(
                new CloseAuctionCommand(new AuctionId(auctionId), resource.transactionId())
        );
        return AuctionDetailsResource.from(auction);
    }

    @PostMapping("/{auctionId}/cancel")
    @Operation(summary = "Administratively cancel an incomplete auction and its participations")
    public AuctionDetailsResource cancel(
            @PathVariable String auctionId,
            @Valid @RequestBody CancelAuctionResource resource
    ) {
        var auction = auctionCommandService.handle(
                new CancelAuctionCommand(new AuctionId(auctionId), resource.reason(), true)
        );
        return AuctionDetailsResource.from(auction);
    }
}
