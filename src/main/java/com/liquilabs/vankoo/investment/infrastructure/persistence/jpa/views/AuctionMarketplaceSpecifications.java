package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views;

import com.liquilabs.vankoo.investment.domain.model.queries.GetMarketplaceAuctionsQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

public final class AuctionMarketplaceSpecifications {

    private AuctionMarketplaceSpecifications() {
    }

    public static Specification<AuctionMarketplaceViewEntity> from(GetMarketplaceAuctionsQuery query) {
        return (root, criteriaQuery, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(root.get("publishedAt").isNotNull());
            predicates.add(root.get("status").in(query.statuses()));
            if (query.currency() != null) {
                predicates.add(builder.equal(root.get("currency"), query.currency()));
            }
            if (query.greenCertified() != null) {
                predicates.add(builder.equal(root.get("greenCertified"), query.greenCertified()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
