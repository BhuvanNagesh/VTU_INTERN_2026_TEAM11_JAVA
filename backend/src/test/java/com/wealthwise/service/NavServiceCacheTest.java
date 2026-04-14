package com.wealthwise.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.*;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  WealthWise — Caffeine Cache Testing
 *  Test Suite ID: TS-CACHE-001
 *
 *  Tests the Caffeine cache library contract used by NavService
 *  (nav_latest + nav_history caches).
 *
 *  NavService uses @EnableAsync and field-level non-injectable RestTemplate,
 *  so it requires @SpringBootTest to instantiate (not plain unit test).
 *
 *  This suite therefore tests at TWO levels:
 *
 *  Group A — CaffeineCacheManager API (Pure library tests, no NavService)
 *    Validates the full cache lifecycle: put, get, evict, clear, key isolation.
 *    These are the core contracts that @Cacheable("nav_latest") relies on.
 *
 *  Group B — TTL Expiry (Caffeine library eviction validation)
 *    1ms TTL + cleanUp() verifies that Caffeine actually expires entries.
 *
 *  NOTE: NavService behavioural tests (WW_* synthetic bypass, DB fallback)
 *  are covered in TS-TC-001 (WealthWiseIntegrationTest) via @SpringBootTest.
 *  Those require Docker (Testcontainers) to validate against a real Spring
 *  context with working @EnableCaching, @Cacheable AOP proxy, and real DB.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@DisplayName("TS-CACHE-001 | Caffeine Cache — Library Contract Tests")
class NavServiceCacheTest {

    /** Builds a Caffeine cache manager with a configurable TTL. */
    private static CaffeineCacheManager buildCacheManager(long ttlMs) {
        CaffeineCacheManager mgr = new CaffeineCacheManager("nav_latest", "nav_history");
        mgr.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(ttlMs))
            .maximumSize(100));
        return mgr;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-CACHE-001..005 | CaffeineCacheManager Programmatic API
    // Validates the exact cache behaviour that NavService's @Cacheable relies on.
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-CACHE-001..005 | CaffeineCacheManager — Lifecycle API")
    class CacheCycleTests {

        @Test
        @DisplayName("TC-CACHE-001 | put() + get(): value stored and retrieved by key")
        void cache_storeAndRetrieve() {
            Cache cache = buildCacheManager(60_000).getCache("nav_latest");
            cache.put("119598", Map.of("nav", "187.43", "source", "mfapi.in"));
            Cache.ValueWrapper hit = cache.get("119598");
            assertThat(hit).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, String> value = (Map<String, String>) hit.get();
            assertThat(value.get("nav")).isEqualTo("187.43");
        }

        @Test
        @DisplayName("TC-CACHE-002 | Cache miss: get() returns null for unknown key")
        void cache_missReturnsNull() {
            Cache cache = buildCacheManager(60_000).getCache("nav_latest");
            assertThat(cache.get("NONEXISTENT_AMFI")).isNull();
        }

        @Test
        @DisplayName("TC-CACHE-003 | Key isolation: two amfiCodes maintain separate entries")
        void cache_keyIsolation() {
            Cache cache = buildCacheManager(60_000).getCache("nav_latest");
            cache.put("119598", Map.of("nav", "187.43"));
            cache.put("100033", Map.of("nav", "52.10"));
            assertThat(cache.get("119598")).isNotNull();
            assertThat(cache.get("100033")).isNotNull();
            // Must be distinct objects — not the same cache entry
            assertThat(cache.get("119598").get()).isNotEqualTo(cache.get("100033").get());
        }

        @Test
        @DisplayName("TC-CACHE-004 | evict(): entry absent after explicit eviction")
        void cache_eviction() {
            Cache cache = buildCacheManager(60_000).getCache("nav_history");
            cache.put("119598", List.of(Map.of("date", "10-04-2026", "nav", "187.43")));
            assertThat(cache.get("119598")).isNotNull(); // present before evict
            cache.evict("119598");
            assertThat(cache.get("119598")).isNull();   // gone after evict
        }

        @Test
        @DisplayName("TC-CACHE-005 | clear(): all entries removed atomically")
        void cache_clearAll() {
            Cache cache = buildCacheManager(60_000).getCache("nav_latest");
            cache.put("119598", Map.of("nav", "187.43"));
            cache.put("100033", Map.of("nav", "52.10"));
            cache.put("119551", Map.of("nav", "30.21"));
            cache.clear();
            assertThat(cache.get("119598")).isNull();
            assertThat(cache.get("100033")).isNull();
            assertThat(cache.get("119551")).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-CACHE-006..007 | Named Cache Isolation
    // nav_latest and nav_history must be SEPARATE cache namespaces.
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-CACHE-006..007 | Named Cache Isolation")
    class NamedCacheIsolationTests {

        @Test
        @DisplayName("TC-CACHE-006 | nav_latest and nav_history are separate caches")
        void namedCaches_areIsolated() {
            CaffeineCacheManager mgr = buildCacheManager(60_000);
            Cache navLatest  = mgr.getCache("nav_latest");
            Cache navHistory = mgr.getCache("nav_history");

            navLatest.put("119598", Map.of("type", "latest"));
            // nav_history should NOT contain the entry put into nav_latest
            assertThat(navHistory.get("119598")).isNull();
        }

        @Test
        @DisplayName("TC-CACHE-007 | Clearing nav_latest does not affect nav_history")
        void clearLatest_doesNotClearHistory() {
            CaffeineCacheManager mgr = buildCacheManager(60_000);
            Cache navLatest  = mgr.getCache("nav_latest");
            Cache navHistory = mgr.getCache("nav_history");

            navHistory.put("119598", List.of(Map.of("date", "10-04-2026", "nav", "187.43")));
            navLatest.clear();
            // nav_history entry must still be there
            assertThat(navHistory.get("119598")).isNotNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TC-CACHE-008..009 | TTL Expiry
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("TC-CACHE-008..009 | TTL Expiry — Caffeine Eviction")
    class TtlExpiryTests {

        @Test
        @DisplayName("TC-CACHE-008 | Entry present immediately after put (within 5s TTL)")
        void entry_presentWithinTtl() {
            Cache cache = buildCacheManager(5_000).getCache("nav_latest");
            cache.put("119598", Map.of("nav", "187.43"));
            assertThat(cache.get("119598")).isNotNull();
        }

        @Test
        @DisplayName("TC-CACHE-009 | Entry absent after TTL expiry (1ms TTL + 50ms sleep + cleanUp())")
        void entry_absentAfterTtlExpiry() throws InterruptedException {
            CaffeineCacheManager mgr = new CaffeineCacheManager("nav_latest");
            mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(1))
                .maximumSize(100));

            Cache cache = mgr.getCache("nav_latest");
            cache.put("119598", Map.of("nav", "187.43"));
            Thread.sleep(50); // Wait well past 1ms TTL

            // Caffeine uses lazy eviction — trigger the cleanup pass explicitly
            com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache =
                (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
            nativeCache.cleanUp();

            assertThat(cache.get("119598")).isNull();
        }
    }
}
