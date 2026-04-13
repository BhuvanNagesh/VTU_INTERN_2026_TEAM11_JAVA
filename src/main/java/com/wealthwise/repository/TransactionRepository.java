package com.wealthwise.repository;

import com.wealthwise.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
