package com.wealthwise.repository;

import com.wealthwise.model.Scheme;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchemeRepository extends JpaRepository<Scheme, Long> {

    Optional<Scheme> findByAmfiCode(String amfiCode);

    boolean existsByAmfiCode(String amfiCode);

    @Query("SELECT s FROM Scheme s WHERE s.isActive = true AND " +
           "(LOWER(s.schemeName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(s.amcName) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Scheme> searchActive(@Param("q") String query, Pageable pageable);

    @Query("SELECT s FROM Scheme s WHERE s.isActive = true AND " +
           "(LOWER(s.schemeName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           " LOWER(s.amcName) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
           "(:category IS NULL OR s.broadCategory = :category) AND " +
           "(:planType IS NULL OR s.planType = :planType)")
    Page<Scheme> searchFiltered(@Param("q") String query,
                                 @Param("category") String category,
                                 @Param("planType") String planType,
                                 Pageable pageable);

    long countByIsActiveTrue();

    List<Scheme> findByBroadCategoryAndIsActiveTrue(String broadCategory, Pageable pageable);
}
