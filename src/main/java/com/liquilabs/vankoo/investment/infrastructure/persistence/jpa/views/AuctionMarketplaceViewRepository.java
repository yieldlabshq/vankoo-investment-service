package com.liquilabs.vankoo.investment.infrastructure.persistence.jpa.views;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuctionMarketplaceViewRepository extends
        JpaRepository<AuctionMarketplaceViewEntity, String>,
        JpaSpecificationExecutor<AuctionMarketplaceViewEntity> {
}
