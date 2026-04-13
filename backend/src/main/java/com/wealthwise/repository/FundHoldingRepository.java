package com.wealthwise.repository;

import com.wealthwise.model.FundHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface FundHoldingRepository extends JpaRepository<FundHolding, Long> {

    List<FundHolding> findBySchemeAmfiCode(String schemeAmfiCode);

    boolean existsBySchemeAmfiCode(String schemeAmfiCode);

    @Query("SELECT DISTINCT f.schemeAmfiCode FROM FundHolding f")
    List<String> findDistinctSchemeAmfiCodes();

    @Modifying
    @Transactional
    @Query("DELETE FROM FundHolding f WHERE f.schemeAmfiCode = :code")
    void deleteBySchemeAmfiCode(@Param("code") String code);

    /** Count how many funds each stock appears in — for high-overlap detection */
    @Query("SELECT f.stockName, f.sector, COUNT(DISTINCT f.schemeAmfiCode) as fundCount " +
           "FROM FundHolding f WHERE f.schemeAmfiCode IN :codes " +
           "GROUP BY f.stockName, f.sector HAVING COUNT(DISTINCT f.schemeAmfiCode) > 1 " +
           "ORDER BY COUNT(DISTINCT f.schemeAmfiCode) DESC")
    List<Object[]> findHighOverlapStocks(@Param("codes") List<String> codes);
}
