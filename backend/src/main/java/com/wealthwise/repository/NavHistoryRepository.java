package com.wealthwise.repository;

import com.wealthwise.model.NavHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NavHistoryRepository extends JpaRepository<NavHistory, Long> {

    /** All NAV records for a scheme, newest date first. */
    List<NavHistory> findByAmfiCodeOrderByNavDateDesc(String amfiCode);

    /** Single NAV for a specific scheme + date. */
    Optional<NavHistory> findByAmfiCodeAndNavDate(String amfiCode, LocalDate navDate);

    /** True if we have ANY NAV history stored for this scheme. */
    boolean existsByAmfiCode(String amfiCode);

    /** Most recent stored date for a scheme (to detect if data is stale). */
    @Query("SELECT MAX(h.navDate) FROM NavHistory h WHERE h.amfiCode = :amfiCode")
    Optional<LocalDate> findLatestDateByAmfiCode(@Param("amfiCode") String amfiCode);

    /** Count of records for a scheme. */
    long countByAmfiCode(String amfiCode);

    /**
     * Atomic upsert-ignore using PostgreSQL ON CONFLICT DO NOTHING.
     * Eliminates duplicate key violations under concurrent requests.
     * Returns 1 if a new row was inserted, 0 if it already existed.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO nav_history (amfi_code, nav_date, nav_value) " +
                   "VALUES (:amfiCode, :navDate, :navValue) " +
                   "ON CONFLICT (amfi_code, nav_date) DO NOTHING",
           nativeQuery = true)
    int upsertIgnore(@Param("amfiCode") String amfiCode,
                     @Param("navDate") LocalDate navDate,
                     @Param("navValue") BigDecimal navValue);
}
