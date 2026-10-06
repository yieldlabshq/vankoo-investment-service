package com.liquilabs.vankoo.investment.infrastructure.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class MarketplaceCacheConfiguration {

    public static final String MARKETPLACE_CACHE = "marketplaceSearch";
    public static final Duration MARKETPLACE_TTL = Duration.ofSeconds(30);

    @Bean
    public CacheManager marketplaceCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MARKETPLACE_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .recordStats()
                .expireAfterWrite(MARKETPLACE_TTL));
        return manager;
    }
}
