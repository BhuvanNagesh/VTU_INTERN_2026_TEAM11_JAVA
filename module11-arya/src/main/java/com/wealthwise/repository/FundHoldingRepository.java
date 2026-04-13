package com.wealthwise.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.wealthwise.entity.FundHolding;

@Repository
public interface FundHoldingRepository extends JpaRepository<FundHolding, UUID> {

    List<FundHolding> findBySchemeId(String schemeId);

    @Query("SELECT DISTINCT f.schemeId FROM FundHolding f")
    List<String> findDistinctSchemeIds();
}