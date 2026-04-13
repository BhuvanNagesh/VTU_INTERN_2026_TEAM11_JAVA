package com.wealthwise.security;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine-backed cache configuration — acts like an in-process Redis.
 *
 * Cache names:
 *   nav_latest   — Latest NAV for a scheme (TTL: 24h, refreshed by daily job)
 *   nav_history  — Historical NAV data from mfapi.in (TTL: 7 days, near-immutable)
 *   scheme_meta  — Scheme metadata (TTL: 1h)
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("nav_latest",
            Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build());

        manager.registerCustomCache("nav_history",
            Caffeine.newBuilder()
                .expireAfterWrite(7, TimeUnit.DAYS)
                .maximumSize(500_000) // Historical data: one entry per scheme+date
                .build());

        manager.registerCustomCache("scheme_meta",
            Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10_000)
                .build());

        return manager;
    }
}
