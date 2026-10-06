package com.liquilabs.vankoo.investment.interfaces.rest.controllers;

import com.liquilabs.vankoo.investment.domain.exceptions.ActiveQuoteNotFoundException;
import com.liquilabs.vankoo.investment.domain.exceptions.AuctionNotFoundException;
import com.liquilabs.vankoo.investment.domain.exceptions.UnauthorizedAccessException;
import com.liquilabs.vankoo.investment.domain.model.commands.*;
import com.liquilabs.vankoo.investment.domain.model.queries.GetActiveFinancialQuoteQuery;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAuctionByIdQuery;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAuctionsByInvestorQuery;
import com.liquilabs.vankoo.investment.domain.model.queries.GetAuctionsByMypeQuery;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.AuctionId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;
import com.liquilabs.vankoo.investment.domain.services.AuctionCommandService;
import com.liquilabs.vankoo.investment.domain.services.AuctionQueryService;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.*;
import com.liquilabs.vankoo.investment.interfaces.rest.transform.CreateAuctionCommandFromResourceAssembler;
import com.liquilabs.vankoo.investment.interfaces.rest.transform.CreatePartitionCommandFromResourceAssembler;
import com.liquilabs.vankoo.investment.interfaces.rest.transform.MarketplaceQueryAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auctions")
@Tag(name = "Auctions", description = "Auction quotes, publication and fixed-rate investments")
public class AuctionsController {

    private final AuctionCommandService auctionCommandService;
    private final AuctionQueryService auctionQueryService;

    public AuctionsController(
            AuctionCommandService auctionCommandService,
            AuctionQueryService auctionQueryService
    ) {
        this.auctionCommandService = auctionCommandService;
        this.auctionQueryService = auctionQueryService;
    }

    @PostMapping
    @Operation(summary = "Create an auction candidate")
    public ResponseEntity<String> createAuction(@Valid @RequestBody CreateAuctionResource resource) {
        var command = CreateAuctionCommandFromResourceAssembler.toCommandFromResource(resource);
        var auctionId = auctionCommandService.handle(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(auctionId.uuid());
    }

    @GetMapping("/{auctionId}")
    @Operation(summary = "Get auction financial and lifecycle details")
    public AuctionDetailsResource getAuction(@PathVariable String auctionId) {
        return AuctionDetailsResource.from(findAuction(auctionId));
    }

    @PostMapping("/{auctionId}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a 24-hour financial quote for an evaluated auction, superseding the active one")
    public FinancialQuoteResource createQuote(
            @PathVariable String auctionId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId
    ) {
        var quote = auctionCommandService.handle(
                new CreateFinancialQuoteCommand(new AuctionId(auctionId), requester(callerId))
        );
        return FinancialQuoteResource.from(quote);
    }

    @GetMapping("/{auctionId}/quotes/active")
    @Operation(summary = "Get the quote the owning MYPE can still accept; 404 when there is none")
    public FinancialQuoteResource getActiveQuote(
            @PathVariable String auctionId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId
    ) {
        var quote = auctionQueryService.handle(
                new GetActiveFinancialQuoteQuery(new AuctionId(auctionId), requester(callerId))
        ).orElseThrow(() -> new ActiveQuoteNotFoundException(auctionId));
        return FinancialQuoteResource.from(quote);
    }

    @PostMapping("/{auctionId}/quotes/{quoteId}/accept")
    @Operation(summary = "Accept a financial quote and publish the auction")
    public AuctionDetailsResource acceptQuote(
            @PathVariable String auctionId,
            @PathVariable String quoteId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId
    ) {
        var auction = auctionCommandService.handle(
                new AcceptFinancialQuoteCommand(new AuctionId(auctionId), quoteId, requester(callerId))
        );
        return AuctionDetailsResource.from(auction);
    }

    @PostMapping("/{auctionId}/investments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy a fixed-rate participation in a published auction")
    public InvestmentResponseResource invest(
            @PathVariable String auctionId,
            @Valid @RequestBody CreateInvestmentResource resource
    ) {
        var command = CreatePartitionCommandFromResourceAssembler.toCommandFromResource(
                new AuctionId(auctionId), resource
        );
        return InvestmentResponseResource.from(auctionCommandService.handle(command));
    }

    @PostMapping("/{auctionId}/cancel")
    @Operation(summary = "Cancel an auction that has no committed investments")
    public AuctionDetailsResource cancel(
            @PathVariable String auctionId,
            @Valid @RequestBody CancelAuctionResource resource
    ) {
        var auction = auctionCommandService.handle(
                new CancelAuctionCommand(new AuctionId(auctionId), resource.reason(), false)
        );
        return AuctionDetailsResource.from(auction);
    }

    @GetMapping("/marketplace")
    @Operation(summary = "List published auctions available for investment")
    public MarketplacePageResource getMarketplaceAuctions(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Boolean greenCertified,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "expiresAt,asc") String sort
    ) {
        var query = MarketplaceQueryAssembler.toQuery(
                status, currency, greenCertified, page, size, sort
        );
        return MarketplacePageResource.from(auctionQueryService.handle(query));
    }

    @GetMapping("/mype/{mypeId}")
    @Operation(summary = "List auctions owned by a MYPE")
    public List<AuctionDetailsResource> getAuctionsByMype(
            @PathVariable String mypeId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId
    ) {
        requireCaller(mypeId, callerId);
        return auctionQueryService.handle(new GetAuctionsByMypeQuery(new UserId(mypeId))).stream()
                .map(AuctionDetailsResource::from)
                .toList();
    }

    @GetMapping("/investor/{investorId}")
    @Operation(summary = "List auctions an investor has participated in")
    public List<AuctionDetailsResource> getAuctionsByInvestor(
            @PathVariable String investorId,
            @RequestHeader(value = "X-User-Id", required = false) String callerId
    ) {
        requireCaller(investorId, callerId);
        return auctionQueryService.handle(new GetAuctionsByInvestorQuery(new UserId(investorId))).stream()
                .map(AuctionDetailsResource::from)
                .toList();
    }

    private void requireCaller(String expectedUserId, String callerId) {
        if (callerId == null || !callerId.equals(expectedUserId)) {
            throw new UnauthorizedAccessException("Caller is not authorized to view this resource");
        }
    }

    /** The gateway injects X-User-Id from the JWT; without it nobody can be the owner. */
    private UserId requester(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            throw new UnauthorizedAccessException("Caller is not identified");
        }
        return new UserId(callerId);
    }

    private com.liquilabs.vankoo.investment.domain.model.aggregates.Auction findAuction(String auctionId) {
        return auctionQueryService.handle(new GetAuctionByIdQuery(new AuctionId(auctionId)))
                .orElseThrow(() -> new AuctionNotFoundException(auctionId));
    }
}
