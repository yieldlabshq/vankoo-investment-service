package com.liquilabs.vankoo.investment.domain.model.commands;

import com.liquilabs.vankoo.investment.domain.model.valueobjects.InvoiceId;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.Money;
import com.liquilabs.vankoo.investment.domain.model.valueobjects.UserId;

import java.time.LocalDate;

public record CreateAuctionCommand(
        InvoiceId invoiceId,
        UserId mypeId,
        Money invoiceAmount,
        boolean greenCertified,

        String payerRuc,
        String payerName,
        LocalDate dueDate
) {}
