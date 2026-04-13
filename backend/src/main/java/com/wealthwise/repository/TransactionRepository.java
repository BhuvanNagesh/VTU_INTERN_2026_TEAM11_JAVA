package com.wealthwise.repository;

import com.wealthwise.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByTransactionDateDescCreatedAtDesc(Long userId);

    Page<Transaction> findByUserIdOrderByTransactionDateDescCreatedAtDesc(Long userId, Pageable pageable);

    List<Transaction> findByUserIdAndSchemeAmfiCodeOrderByTransactionDateAsc(Long userId, String schemeAmfiCode);

    List<Transaction> findByUserIdAndFolioNumberOrderByTransactionDateAsc(Long userId, String folioNumber);

    Optional<Transaction> findByTransactionRef(String transactionRef);

    @Query("SELECT DISTINCT t.schemeAmfiCode FROM Transaction t WHERE t.userId = :userId")
    List<String> findDistinctSchemesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT t.folioNumber FROM Transaction t WHERE t.userId = :userId AND t.folioNumber IS NOT NULL")
    List<String> findDistinctFoliosByUserId(@Param("userId") Long userId);

    List<Transaction> findByUserIdAndTransactionDateBetween(Long userId, LocalDate from, LocalDate to);

    // Used by reconciliation service to remap WW_ISIN_ codes to real AMFI codes
    List<Transaction> findBySchemeAmfiCode(String schemeAmfiCode);

    @Transactional
    @Modifying
    @Query("UPDATE Transaction t SET t.schemeAmfiCode = :newCode, t.schemeName = :newName WHERE t.schemeAmfiCode = :oldCode")
    int bulkUpdateSchemeAmfiCode(@Param("oldCode") String oldCode,
                                 @Param("newCode") String newCode,
                                 @Param("newName") String newName);
}
