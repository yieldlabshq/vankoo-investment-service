package com.liquilabs.vankoo.investment.interfaces.rest.transform;

import com.liquilabs.vankoo.investment.domain.model.commands.CreateAuctionCommand;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.*;
import com.liquilabs.vankoo.investment.interfaces.rest.resources.CreateAuctionResource;

public class CreateAuctionCommandFromResourceAssembler {

    public static CreateAuctionCommand toCommandFromResource(CreateAuctionResource resource) {
        return new CreateAuctionCommand(
                new InvoiceId(resource.invoiceId()),
                new UserId(resource.mypeId()),
                new Money(resource.invoiceAmount(), Currency.valueOf(resource.currency().toUpperCase())),
                resource.greenCertified(),
                resource.payerRuc(),
                resource.payerName(),
                resource.dueDate()
        );
    }
}
