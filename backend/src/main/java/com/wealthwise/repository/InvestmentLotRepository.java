package com.wealthwise.repository;

import com.wealthwise.model.InvestmentLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface InvestmentLotRepository extends JpaRepository<InvestmentLot, Long> {

    // FIFO: oldest lots first for redemption consumption
    List<InvestmentLot> findByUserIdAndSchemeAmfiCodeAndFolioNumberOrderByPurchaseDateAsc(
            Long userId, String schemeAmfiCode, String folioNumber);

    List<InvestmentLot> findByUserIdAndSchemeAmfiCodeOrderByPurchaseDateAsc(
            Long userId, String schemeAmfiCode);

    List<InvestmentLot> findByUserIdOrderByPurchaseDateAsc(Long userId);

    List<InvestmentLot> findByTransactionId(Long transactionId);

    @Transactional
    @Modifying
    @Query("DELETE FROM InvestmentLot l WHERE l.transactionId = :transactionId")
    void deleteByTransactionId(@Param("transactionId") Long transactionId);

    // Used by reconciliation service to remap WW_ISIN_ codes to real AMFI codes
    List<InvestmentLot> findBySchemeAmfiCode(String schemeAmfiCode);

    @Transactional
    @Modifying
    @Query("UPDATE InvestmentLot l SET l.schemeAmfiCode = :newCode, l.schemeName = :newName WHERE l.schemeAmfiCode = :oldCode")
    int bulkUpdateSchemeAmfiCode(@Param("oldCode") String oldCode,
                                 @Param("newCode") String newCode,
                                 @Param("newName") String newName);
}
