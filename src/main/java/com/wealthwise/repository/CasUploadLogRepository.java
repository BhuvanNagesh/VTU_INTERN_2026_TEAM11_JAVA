package com.wealthwise.repository;

import com.wealthwise.model.CasUploadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CasUploadLogRepository extends JpaRepository<CasUploadLog, Long> {
}
